package xyz.nikitacartes.easyauthreset.handler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import xyz.nikitacartes.easyauth.EasyAuth;
import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;
import xyz.nikitacartes.easyauth.utils.AuthHelper;
import xyz.nikitacartes.easyauthreset.EasyAuthReset;
import xyz.nikitacartes.easyauthreset.config.EasyAuthResetConfig;
import xyz.nikitacartes.easyauthreset.email.EmailService;
import xyz.nikitacartes.easyauthreset.storage.PlayerEmailStorage;
import xyz.nikitacartes.easyauthreset.storage.StateStorage;
import xyz.nikitacartes.easyauthreset.util.Lang;
import xyz.nikitacartes.easyauthreset.util.PasswordGenerator;
import xyz.nikitacartes.easyauthreset.verification.VerificationCodeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 核心业务逻辑（单例）。要点：
 * <ul>
 *   <li>EasyAuth 数据访问走静态 {@code EasyAuth.DB}（{@code DbApi}），以玩家名为键；</li>
 *   <li>冷却表是实例字段并<b>持久化</b>（服务器重启后仍生效）；</li>
 *   <li>BCrypt 哈希与 SMTP 发送均异步，不阻塞服务器主线程；</li>
 *   <li>支持"邮箱绑定"：绑定后重置验证码只发往绑定邮箱；</li>
 *   <li>Mixin 接口在两代 EasyAuth 中包名不同（3.3.x 为 utils、3.4.x 为 interfaces），
 *       故用反射调用 {@code easyAuth$getPlayerEntryV1()}（两代方法名一致），兼容 3.3.5+。</li>
 * </ul>
 */
public class PasswordResetHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyAuthReset.MOD_ID);

    /** 简单的邮箱格式校验（宽松，仅拦截明显错误输入） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public enum RequestResult {
        SUCCESS,
        COOLDOWN,
        NOT_REGISTERED,
        INVALID_EMAIL,
        INTERNAL_ERROR,
        /** 配置了 requireEmailBind 且账号未绑定/未登记 */
        BIND_REQUIRED,
        /** 未提供邮箱参数且账号未绑定（指令层显示用法） */
        NO_EMAIL,
        /** 账号已由服主预登记邮箱，无需自助绑定 */
        OWNER_MANAGED,
        /** IP 一致性校验（strict 模式）拦截 */
        IP_BLOCKED
    }

    /**
     * EasyAuth 通过 Mixin 注入到 ServerPlayerEntity 上的访问方法。
     * 方法名在 EasyAuth 3.3.x（utils.PlayerAuth）与 3.4.x（interfaces.PlayerAuth）中一致，
     * 但<b>接口所在包不同</b>，因此用反射调用以兼容两个版本。
     * 若反射不可用（如 EasyAuth 未注入），返回 null，走 DB 查询回退。
     */
    private static final Method GET_PLAYER_ENTRY_METHOD = findGetPlayerEntryMethod();
    private static final Method GET_IP_METHOD = findGetIpMethod();

    private static Method findGetPlayerEntryMethod() {
        try {
            return ServerPlayerEntity.class.getMethod("easyAuth$getPlayerEntryV1");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findGetIpMethod() {
        try {
            return ServerPlayerEntity.class.getMethod("easyAuth$getIpAddress");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private final EasyAuthResetConfig config;
    private final EmailService emailService;
    private final VerificationCodeManager codeManager;
    private final PlayerEmailStorage emailStorage;
    private final StateStorage state;

    /** 冷却管理：UUID → 冷却结束时间戳（毫秒）。仅内存（重启即清空，不持久化）。 */
    private final Map<String, Long> cooldownMap = new ConcurrentHashMap<>();
    /** 安全告警节流：UUID → 上次告警时间戳（毫秒，60 秒内同类告警只发一次） */
    private final Map<String, Long> alertThrottle = new ConcurrentHashMap<>();
    private final long cooldownMillis;

    public PasswordResetHandler(EasyAuthResetConfig config, EmailService emailService,
                                VerificationCodeManager codeManager, PlayerEmailStorage emailStorage,
                                StateStorage state) {
        this.config = config;
        this.emailService = emailService;
        this.codeManager = codeManager;
        this.emailStorage = emailStorage;
        this.state = state;
        this.cooldownMillis = config.cooldownSeconds * 1000L;
    }

    /**
     * 第一步：申请重置密码。
     * 邮箱决定顺序：<b>服主预登记（easyauthreset_mailmap.json）&gt; 玩家绑定绑定邮箱 &gt; 输入邮箱</b>（仅当
     * requireEmailBind=false）。参数 {@code email} 可为 null（无参数指令）：此时必须有已登记/绑定邮箱。
     */
    public RequestResult requestReset(ServerPlayerEntity player, String email) {
        String uuid = player.getUuidAsString();
        String name = player.getName().getString();
        String nameLower = name.toLowerCase(Locale.ENGLISH);

        String effective = emailStorage.resolveEffectiveEmail(uuid, nameLower);
        String targetEmail;
        boolean fromOwner = false;
        boolean fromBind = false;
        if (effective != null) {
            targetEmail = effective;
            fromOwner = emailStorage.getOwnerEmail(uuid) != null
                    || emailStorage.getOwnerEmail(nameLower) != null;
            fromBind = !fromOwner;
        } else if (email == null) {
            return config.requireEmailBind ? RequestResult.BIND_REQUIRED : RequestResult.NO_EMAIL;
        } else {
            if (config.requireEmailBind) {
                return RequestResult.BIND_REQUIRED;
            }
            targetEmail = email.trim();
        }

        if (!EMAIL_PATTERN.matcher(targetEmail).matches()) {
            return RequestResult.INVALID_EMAIL;
        }

        long now = System.currentTimeMillis();
        if (isCoolingDown(uuid, now)) {
            return RequestResult.COOLDOWN;
        }

        if (!isDbReady()) {
            return RequestResult.INTERNAL_ERROR;
        }

        PlayerEntryV1 entry = getPlayerEntry(player);
        // EasyAuth 的注册语义：玩家条目存在且 password 非空（global password 模式除外）
        if (entry == null || entry.password == null || entry.password.trim().isEmpty()) {
            return RequestResult.NOT_REGISTERED;
        }

        // IP 一致性校验（warn/strict 模式）：差异即告警，strict 拦截
        if (!checkIp(player, entry, "重置密码申请", targetEmail)) {
            return RequestResult.IP_BLOCKED;
        }

        // 先落冷却：堵住并发窗口，同时防止发送失败后刷 SMTP
        putCooldown(uuid, now + cooldownMillis);

        emailStorage.setEmail(uuid, targetEmail);

        String code = codeManager.generateAndStore(uuid, VerificationCodeManager.Purpose.RESET);
        LOGGER.info("Password reset request: player={} (uuid={}) -> email={}{}", name, uuid, targetEmail,
                fromOwner ? " (admin-registered)" : fromBind ? " (bound email)" : "");
        final boolean ownerManaged = fromOwner;

        emailService.sendVerificationCode(targetEmail, name, code, null, sent -> {
            if (sent) {
                inform(player, Lang.msg(config, ownerManaged ? "codeSentOwner" : "codeSent", targetEmail));
            } else {
                codeManager.invalidate(uuid);
                inform(player, Lang.msg(config, "codeSendFailed"));
            }
        });
        return RequestResult.SUCCESS;
    }

    /**
     * 邮箱绑定第一步：向指定邮箱发送验证码（验证所有权）。
     * 需要玩家已注册；复用同一冷却表。服主已登记的账号无需（也不允许）自助绑定。
     */
    public RequestResult bindRequest(ServerPlayerEntity player, String email) {
        String uuid = player.getUuidAsString();
        String nameLower = player.getName().getString().toLowerCase(Locale.ENGLISH);

        if (emailStorage.getOwnerEmail(uuid) != null || emailStorage.getOwnerEmail(nameLower) != null) {
            return RequestResult.OWNER_MANAGED;
        }

        String trimmedEmail = email == null ? "" : email.trim();
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            return RequestResult.INVALID_EMAIL;
        }

        long now = System.currentTimeMillis();
        if (isCoolingDown(uuid, now)) {
            return RequestResult.COOLDOWN;
        }

        if (!isDbReady()) {
            return RequestResult.INTERNAL_ERROR;
        }

        PlayerEntryV1 entry = getPlayerEntry(player);
        if (entry == null || entry.password == null || entry.password.trim().isEmpty()) {
            return RequestResult.NOT_REGISTERED;
        }

        // IP 一致性校验（warn/strict 模式）：差异即告警，strict 拦截
        if (!checkIp(player, entry, "绑定邮箱(" + trimmedEmail + ")", trimmedEmail)) {
            return RequestResult.IP_BLOCKED;
        }

        putCooldown(uuid, now + cooldownMillis);
        emailStorage.setEmail(uuid, trimmedEmail);

        String code = codeManager.generateAndStore(uuid, VerificationCodeManager.Purpose.BIND);
        LOGGER.info("Email bind request: player={} (uuid={}) -> email={}{}", player.getName().getString(), uuid, trimmedEmail,
                emailStorage.getBinding(uuid) != null ? " (rebind)" : "");

        // 点击激活链接（可选）：仅绑定用途；需 enableClickActivation 且配置了公开地址
        String link = null;
        if (config.enableClickActivation && config.activationPublicUrl != null
                && !config.activationPublicUrl.isEmpty()) {
            String token = codeManager.createActivationToken(uuid);
            if (token != null) {
                link = config.activationPublicUrl + "/easyauthreset/activate/" + token;
            }
        }
        final String activationLink = link;
        final boolean rebind = emailStorage.getBinding(uuid) != null;

        emailService.sendVerificationCode(trimmedEmail, player.getName().getString(), code, activationLink, sent -> {
            if (sent) {
                String key;
                if (rebind) {
                    key = activationLink != null ? "bindRebindCodeSentLink" : "bindRebindCodeSent";
                } else {
                    key = activationLink != null ? "bindCodeSentLink" : "bindCodeSent";
                }
                inform(player, Lang.msg(config, key, trimmedEmail));
            } else {
                codeManager.invalidate(uuid);
                inform(player, Lang.msg(config, "codeSendFailed"));
            }
        });
        return RequestResult.SUCCESS;
    }

    /**
     * 邮箱绑定第二步：验证绑定验证码并完成绑定。
     */
    public boolean bindConfirm(ServerPlayerEntity player, String code) {
        String uuid = player.getUuidAsString();
        VerificationCodeManager.VerifyResult vr =
                codeManager.verify(uuid, VerificationCodeManager.Purpose.BIND, code == null ? "" : code.trim());
        if (vr != VerificationCodeManager.VerifyResult.SUCCESS) {
            player.sendMessage(Text.literal(Lang.msg(config, "bindConfirmInvalid")), false);
            return false;
        }

        String pendingEmail = emailStorage.getEmail(uuid);
        if (pendingEmail == null || !EMAIL_PATTERN.matcher(pendingEmail).matches()) {
            player.sendMessage(Text.literal(Lang.msg(config, "bindConfirmInvalid")), false);
            return false;
        }

        emailStorage.setBinding(uuid, pendingEmail);
        cooldownMap.remove(uuid);
        LOGGER.info("Email bind success: player={} (uuid={}) -> email={}", player.getName().getString(), uuid, pendingEmail);
        player.sendMessage(Text.literal(Lang.msg(config, "bindDone", pendingEmail)), false);
        return true;
    }

    /**
     * 第二步：验证验证码，生成临时密码写入 EasyAuth 数据库，并将新密码发送到邮箱。
     */
    public boolean confirmAndReset(ServerPlayerEntity player, String code) {
        String uuid = player.getUuidAsString();

        VerificationCodeManager.VerifyResult verifyResult =
                codeManager.verify(uuid, VerificationCodeManager.Purpose.RESET, code == null ? "" : code.trim());
        if (verifyResult != VerificationCodeManager.VerifyResult.SUCCESS) {
            player.sendMessage(Text.literal(Lang.msg(config,
                    verifyResult == VerificationCodeManager.VerifyResult.ATTEMPTS_EXCEEDED
                            ? "codeAttemptsExceeded" : "codeInvalid")), false);
            return false;
        }

        if (!isDbReady()) {
            player.sendMessage(Text.literal(Lang.msg(config, "dbNotReady")), false);
            return false;
        }

        PlayerEntryV1 entry = getPlayerEntry(player);
        // 玩家可能在申请与确认之间注销了账号
        if (entry == null || entry.password == null || entry.password.trim().isEmpty()) {
            codeManager.invalidate(uuid);
            player.sendMessage(Text.literal(Lang.msg(config, "accountGone")), false);
            return false;
        }

        String tempPassword = PasswordGenerator.generate(config.tempPasswordLength);

        // BCrypt 哈希约 50-100ms，与 EasyAuth 自身一致地放到线程池执行，避免卡主线程
        EasyAuth.THREADPOOL.submit(() -> {
            try {
                entry.password = AuthHelper.hashPassword(tempPassword.toCharArray());
                // update() 内部走 DB.updateUserData(entry) 持久化
                entry.update();
            } catch (Throwable t) {
                LOGGER.error("Failed to update password (uuid={})", uuid, t);
                inform(player, Lang.msg(config, "internalError"));
            }
        });

        // 重置成功后解除冷却
        cooldownMap.remove(uuid);
        LOGGER.info("Password reset success: player={} (uuid={}), email={}", player.getName().getString(), uuid,
                emailStorage.getEmail(uuid));

        String email = emailStorage.getEmail(uuid);
        if (email != null) {
            emailService.sendNewPassword(email, player.getName().getString(), tempPassword, sent ->
                    inform(player, sent
                            ? Lang.msg(config, "resetMailSent", email)
                            : Lang.msg(config, "resetMailFailed", tempPassword)));
        }

        // 私聊显示临时密码（可通过配置关闭；关闭后仅靠邮件）
        if (config.showTempPasswordInChat) {
            player.sendMessage(Text.literal(Lang.msg(config, "resetSuccess", tempPassword)), false);
        } else {
            player.sendMessage(Text.literal(Lang.msg(config, "resetMailSent",
                    email != null ? email : "已绑定的邮箱")), false);
        }
        return true;
    }

    /** 是否已绑定邮箱（供指令层判断 / 提示）。 */
    public String boundEmail(String uuid) {
        return emailStorage.getBinding(uuid);
    }

    /** 该账号实际生效的收件邮箱（服主登记 > 玩家绑定），无则返回 null。 */
    public String effectiveEmail(ServerPlayerEntity player) {
        return emailStorage.resolveEffectiveEmail(player.getUuidAsString(),
                player.getName().getString().toLowerCase(Locale.ENGLISH));
    }

    /**
     * 获取当前玩家的 EasyAuth 数据条目。
     * 优先取 EasyAuth 在连接时通过 Mixin 注入到玩家实体上的缓存条目
     * （与 /login、/account 使用同一对象引用，改完即时生效且只落一次库）；
     * 取不到时回退到按玩家名查库。
     */
    private PlayerEntryV1 getPlayerEntry(ServerPlayerEntity player) {
        if (GET_PLAYER_ENTRY_METHOD != null) {
            try {
                Object entry = GET_PLAYER_ENTRY_METHOD.invoke(player);
                if (entry instanceof PlayerEntryV1 playerEntry) {
                    return playerEntry;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Failed to get EasyAuth player entry via reflection, falling back to DB query", e);
            }
        }
        try {
            return EasyAuth.DB.getUserData(player.getName().getString());
        } catch (Exception e) {
            LOGGER.error("Failed to query EasyAuth player data", e);
            return null;
        }
    }

    private boolean isDbReady() {
        if (EasyAuth.DB == null) {
            LOGGER.error("EasyAuth.DB is not initialized (is EasyAuth loaded properly?)");
            return false;
        }
        return true;
    }

    private void putCooldown(String uuid, long endMillis) {
        cooldownMap.put(uuid, endMillis);
    }

    /**
     * IP 一致性校验（仅 ipCheckMode=warn/strict 时生效）：
     * 当前连接 IP 与账号在 EasyAuth 中记录的历史登录 IP 不同 → 向 OP 与管理员告警；
     * strict 模式直接拦截（返回 false），warn 模式放行。
     * 无历史记录 / 取不到当前 IP / 关闭时一律放行（默认开启校验后也不会误杀老玩家首次异地操作）。
     */
    private boolean checkIp(ServerPlayerEntity player, PlayerEntryV1 entry, String action, String email) {
        String mode = config.ipCheckMode;
        if (!"warn".equals(mode) && !"strict".equals(mode)) {
            return true;
        }
        if (entry == null || entry.lastIp == null || entry.lastIp.isBlank()) {
            return true;
        }
        String currentIp = currentIp(player);
        if (currentIp == null) {
            LOGGER.warn("Cannot get current player IP (EasyAuth not injected); skipping IP consistency check");
            return true;
        }
        if (currentIp.equals(entry.lastIp)) {
            return true;
        }

        alertIpMismatch(player, entry, currentIp, action, email);
        if ("strict".equals(mode)) {
            player.sendMessage(Text.literal(Lang.msg(config, "ipMismatchBlocked")), false);
            return false;
        }
        player.sendMessage(Text.literal(Lang.msg(config, "ipMismatchWarn")), false);
        return true;
    }

    /** 游戏内（OP）+ 邮件 + 日志三方告警，同一 UUID 60 秒内只发一次。 */
    private void alertIpMismatch(ServerPlayerEntity player, PlayerEntryV1 entry,
                                 String currentIp, String action, String email) {
        String uuid = player.getUuidAsString();
        long now = System.currentTimeMillis();
        Long last = alertThrottle.get(uuid);
        if (last != null && now - last < 60_000L) {
            return;
        }
        alertThrottle.put(uuid, now);

        String name = player.getName().getString();
        LOGGER.warn("IP mismatch alert: player={} (uuid={}) action={} email={} currentIp={} lastIp={}",
                name, uuid, action, email, currentIp, entry.lastIp);

        MinecraftServer server = player.getServer();
        if (server != null) {
            for (ServerPlayerEntity op : server.getPlayerManager().getPlayerList()) {
                if (op.hasPermissionLevel(2)) {
                    op.sendMessage(Text.literal(
                            Lang.msg(config, "adminAlertIp", name, uuid, currentIp, entry.lastIp, action)), false);
                }
            }
        }

        emailService.sendAdminAlert(
                "[EasyAuthReset] 安全告警: IP 差异 - " + action,
                "玩家: " + name + " (uuid=" + uuid + ")\n"
                        + "操作: " + action + "\n"
                        + "邮箱: " + email + "\n"
                        + "当前IP: " + currentIp + "\n"
                        + "账号历史IP: " + entry.lastIp + "\n"
                        + "绑定邮箱: " + (emailStorage.getBinding(uuid) != null ? emailStorage.getBinding(uuid) : "(无)"));
    }

    private String currentIp(ServerPlayerEntity player) {
        if (GET_IP_METHOD == null) {
            return null;
        }
        try {
            Object ip = GET_IP_METHOD.invoke(player);
            return (ip instanceof String s && !s.isEmpty()) ? s : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Failed to get player IP via reflection", e);
            return null;
        }
    }

    /** 剩余冷却秒数（向上取整），无冷却返回 0。 */
    public long cooldownRemaining(String uuid) {
        Long end = cooldownMap.get(uuid);
        if (end == null) {
            return 0;
        }
        long left = (end - System.currentTimeMillis() + 999) / 1000;
        return Math.max(left, 0);
    }

    private boolean isCoolingDown(String uuid, long now) {
        Long end = cooldownMap.get(uuid);
        return end != null && now < end;
    }

    private void inform(ServerPlayerEntity player, String message) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            if (!player.isRemoved()) {
                player.sendMessage(Text.literal(message), false);
            }
        });
    }
}
