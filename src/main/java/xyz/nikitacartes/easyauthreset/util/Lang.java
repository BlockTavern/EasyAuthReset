package xyz.nikitacartes.easyauthreset.util;

import xyz.nikitacartes.easyauthreset.config.EasyAuthResetConfig;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * 轻量双语文案（zh / en）+ 统一消息前缀。
 *
 * <p><b>配色约定</b>（§ 颜色码，1.16+ 客户端均支持）：
 * <ul>
 *   <li>§a 绿 = 成功；§e 黄 = 进行中/提示；§c 红 = 错误；§6 金 = 警告上下文</li>
 *   <li>§b 青 = 邮箱地址；§e 上叠加的命令用 §b/§f 区分；§7 灰 = 次要说明</li>
 * </ul></p>
 */
public final class Lang {
    private static final Map<String, Map<String, String>> TABLES = new HashMap<>();

    static {
        Map<String, String> zh = new HashMap<>();
        zh.put("usage", "§e用法：§f/resetpassword <邮箱> §e| §f/resetpassword confirm <验证码>\n"
                + "§e       §f/resetpassword bind <邮箱> §e| §f/resetpassword bind confirm <验证码>");
        zh.put("onlyPlayers", "§c此命令只能由玩家执行");
        zh.put("sendingCode", "§e正在发送验证码至 §b{0}§e，请稍候…");
        zh.put("sendingCodeBound", "§e该账号已绑定邮箱，正在向绑定邮箱 §b{0}§e 发送验证码，请稍候…");
        zh.put("codeSent", "§a验证码已发送至 §b{0}§a。\n§7若等待期间被踢出，请重新连接后直接输入 §e/resetpassword confirm <验证码>§7 完成重置。");
        zh.put("codeSentOwner", "§a验证码已发送至你登记的邮箱 §b{0}§a。\n§7若等待期间被踢出，请重新连接后直接输入 §e/resetpassword confirm <验证码>§7 完成重置。");
        zh.put("bindCodeSent", "§a绑定验证码已发送至 §b{0}§a。\n§7输入 §e/resetpassword bind confirm <验证码>§7 完成绑定，之后重置将只发往该邮箱。");
        zh.put("bindCodeSentLink", "§a绑定验证邮件已发送至 §b{0}§a（§6内含一次性激活链接§a）。\n§7点击邮件链接，或输入 §e/resetpassword bind confirm <验证码>§7 完成绑定。");
        zh.put("bindRebindCodeSent", "§6将更换绑定邮箱：§e验证码已发送至新邮箱 §b{0}§e。\n§7输入 §e/resetpassword bind confirm <验证码>§7 完成更换（§6无需联系管理员§7）。");
        zh.put("bindRebindCodeSentLink", "§6将更换绑定邮箱：§e验证邮件已发送至新邮箱 §b{0}§e（§6内含一次性激活链接§e）。\n§7点击邮件链接，或输入 §e/resetpassword bind confirm <验证码>§7 完成更换（§6无需联系管理员§7）。");
        zh.put("codeSendFailed", "§c验证码邮件发送失败，请检查邮箱地址或稍后重试（已进入冷却）。");
        zh.put("cooldown", "§c操作过于频繁，请等待 §e{0} §c秒后再试。");
        zh.put("notRegistered", "§c该账号未在服务器注册，无法重置密码。");
        zh.put("notRegisteredBind", "§c仅已注册玩家可验证并绑定邮箱。");
        zh.put("invalidEmail", "§c邮箱格式无效，请检查后重试。");
        zh.put("dbNotReady", "§cEasyAuth 数据库未就绪，请稍后再试。");
        zh.put("codeInvalid", "§c验证码错误或已过期，请重新申请。");
        zh.put("codeAttemptsExceeded", "§c验证码错误次数过多，已作废，请重新申请。");
        zh.put("accountGone", "§c无法确认你的注册账号（账号可能已注销）。");
        zh.put("resetSuccess", "§a密码已重置！临时密码：§b{0}§a\n"
                + "§7登录：§e/login {0}§7\n"
                + "§7登录后立即修改：§e/account changePassword {0} <你的新密码>");
        zh.put("resetMailSent", "§a新密码邮件已发送至 §b{0}§a。");
        zh.put("resetMailFailed", "§c密码已重置，但邮件发送失败！临时密码：§b{0} §c请立即截图保存并联系管理员。");
        zh.put("bindRequired", "§6该账号尚未绑定邮箱，§e请先执行 §f/resetpassword bind <你的邮箱>§e 验证并绑定。");
        zh.put("bindDone", "§a绑定成功！该账号的邮箱为 §b{0}§a，之后重置密码只会发送到该邮箱。\n§7现在可直接输入 §e/resetpassword§7 申请重置。");
        zh.put("bindConfirmInvalid", "§c绑定验证码错误或已过期，请重新执行 §e/resetpassword bind <邮箱>§c。");
        zh.put("bindNotNeeded", "§6该账号已由服主登记邮箱，无需自助绑定。");
        zh.put("internalError", "§c内部错误，请稍后再试。");
        zh.put("ipMismatchBlocked", "§c安全保护：检测到当前网络环境与账号历史登录 IP 不同，本次操作已暂停并向管理员告警。如有疑问请联系管理员。");
        zh.put("ipMismatchWarn", "§6安全提示：§e当前网络环境与账号历史登录 IP 不同，本次操作已放行并向管理员告警。如有疑问请联系管理员。");
        zh.put("adminAlertIp", "§c⚠️ EasyAuthReset 安全告警：§f{0} §c玩家 §f{1} §c(uuid={2}) §c当前IP=§f{3} §c历史IP=§f{4} §c操作=§f{5}");

        Map<String, String> en = new HashMap<>();
        en.put("usage", "§eUsage: §f/resetpassword <email> §e| §f/resetpassword confirm <code>\n"
                + "§e       §f/resetpassword bind <email> §e| §f/resetpassword bind confirm <code>");
        en.put("onlyPlayers", "§cThis command can only be used by players");
        en.put("sendingCode", "§eSending verification code to §b{0}§e, please wait…");
        en.put("sendingCodeBound", "§eAccount has a bound email — sending verification code to §b{0}§e, please wait…");
        en.put("codeSent", "§aVerification code sent to §b{0}§a.\n§7If you were kicked while waiting, reconnect and run §e/resetpassword confirm <code>§7.");
        en.put("codeSentOwner", "§aVerification code sent to your registered email §b{0}§a.\n§7If you were kicked while waiting, reconnect and run §e/resetpassword confirm <code>§7.");
        en.put("bindCodeSent", "§aBinding verification code sent to §b{0}§a.\n§7Run §e/resetpassword bind confirm <code>§7 to finish; future resets will only go to this email.");
        en.put("bindCodeSentLink", "§aBinding email sent to §b{0}§a (§6contains a one-time activation link§a).\n§7Click the link, or run §e/resetpassword bind confirm <code>§7 to finish.");
        en.put("bindRebindCodeSent", "§6Changing bound email:§e verification code sent to §b{0}§e.\n§7Run §e/resetpassword bind confirm <code>§7 to finish (§6no admin needed§7).");
        en.put("bindRebindCodeSentLink", "§6Changing bound email:§e email sent to §b{0}§e (§6contains a one-time activation link§e).\n§7Click the link, or run §e/resetpassword bind confirm <code>§7 to finish (§6no admin needed§7).");
        en.put("codeSendFailed", "§cFailed to send the code email. Check the address or try again later (cooldown applied).");
        en.put("cooldown", "§cToo many requests. Try again in §e{0} §cseconds.");
        en.put("notRegistered", "§cThis account is not registered on the server.");
        en.put("notRegisteredBind", "§cOnly registered players can verify and bind an email.");
        en.put("invalidEmail", "§cInvalid email format.");
        en.put("dbNotReady", "§cEasyAuth database is not ready. Try again later.");
        en.put("codeInvalid", "§cWrong or expired code. Please request a new one.");
        en.put("codeAttemptsExceeded", "§cToo many wrong attempts, code invalidated. Request a new one.");
        en.put("accountGone", "§cCould not verify your registered account (maybe it was removed).");
        en.put("resetSuccess", "§aPassword reset! Temporary password: §b{0}§a\n"
                + "§7Login: §e/login {0}§7\n"
                + "§7Then change it: §e/account changePassword {0} <your new password>");
        en.put("resetMailSent", "§aNew password email sent to §b{0}§a.");
        en.put("resetMailFailed", "§cPassword has been reset but the email failed! Temporary password: §b{0} §cSave it now and contact an admin.");
        en.put("bindRequired", "§6This account has no bound email. §eFirst run §f/resetpassword bind <your email>§e.");
        en.put("bindDone", "§aBound successfully! Reset emails for this account will only go to §b{0}§a.\n§7You can now run §e/resetpassword§7.");
        en.put("bindConfirmInvalid", "§cBinding code wrong or expired. Run §e/resetpassword bind <email>§c again.");
        en.put("bindNotNeeded", "§6This account has an admin-configured email; no need to bind.");
        en.put("internalError", "§cInternal error. Try again later.");
        en.put("ipMismatchBlocked", "§cSecurity protection: your current IP differs from the account last login IP. Action blocked and admins alerted.");
        en.put("ipMismatchWarn", "§6Security notice: §eyour current IP differs from the account last login IP. Action allowed and admins alerted.");
        en.put("adminAlertIp", "§c⚠️ EasyAuthReset SECURITY ALERT: §f{0} §cplayer §f{1} §c(uuid={2}) §ccurrentIp=§f{3} §clastIp=§f{4} §caction=§f{5}");

        TABLES.put("zh", zh);
        TABLES.put("en", en);
    }

    private Lang() {
    }

    /** 按语言 + 前缀组装最终玩家消息。 */
    public static String msg(EasyAuthResetConfig config, String key, Object... args) {
        String text = format(config.language, key, args);
        if (config.messagePrefix == null || config.messagePrefix.isEmpty()) {
            return text;
        }
        return config.messagePrefix + text;
    }

    /** 格式化为指定语言文本（不带头缀），未知 key 回退 en 再到 key 本身。 */
    public static String format(String language, String key, Object... args) {
        Map<String, String> table = TABLES.getOrDefault(language, TABLES.get("zh"));
        String pattern = table.get(key);
        if (pattern == null) {
            pattern = TABLES.get("en").get(key);
        }
        if (pattern == null) {
            return key;
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }
}
