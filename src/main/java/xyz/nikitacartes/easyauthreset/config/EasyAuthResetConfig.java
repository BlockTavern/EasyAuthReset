package xyz.nikitacartes.easyauthreset.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nikitacartes.easyauthreset.EasyAuthReset;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 模组配置：{@code config/easyauthreset.json}。
 *
 * <p><b>与原设计的关键差异：</b>Gson 反序列化时不会调用构造函数，
 * 缺失的字段会变成 null/0 而不是默认值。这里改为"默认实例 + 逐字段合并 + 范围校验"，
 * 保证配置文件被手工删改后仍能安全启动。</p>
 */
public class EasyAuthResetConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyAuthReset.MOD_ID);

    // ---- Gmail SMTP ----
    public String smtpHost = "smtp.gmail.com";
    public int smtpPort = 587;
    /** 是否启用 STARTTLS（587 端口，Gmail 默认） */
    public boolean smtpTls = true;
    /** 是否启用 SSL（465 端口；启用时会覆盖 STARTTLS） */
    public boolean smtpSsl = false;
    public String emailSender = "your-email@gmail.com";
    /** Gmail 应用专用密码（非登录密码），在 Google 账号开启两步验证后生成 */
    public String emailPassword = "";
    /**
     * 从环境变量读取 SMTP 密码（优先于 emailPassword）。
     * 例如填 EASTAUTHRESET_SMTP_PASSWORD 后，在启动脚本中 export 该变量即可避免明文入库。
     */
    public String emailPasswordEnvVar = "";
    /** SMTP 连接/读写超时（毫秒） */
    public int smtpTimeoutMillis = 15000;
    /** 发送失败后的额外重试次数（0 = 不重试） */
    public int smtpRetries = 1;

    // ---- 验证码 ----
    /** 验证码有效期（秒） */
    public int codeExpirySeconds = 300;
    /** 验证码位数 */
    public int codeLength = 6;
    /** 单个验证码允许的错误尝试次数，超过即作废 */
    public int maxCodeAttempts = 5;

    // ---- 临时密码 ----
    public int tempPasswordLength = 12;
    /** 是否在游戏内私聊消息中显示临时密码（SMTP 故障时的自救途径；关闭后仅靠邮件） */
    public boolean showTempPasswordInChat = true;

    // ---- 冷却 ----
    /** 同一 UUID 两次申请之间的最短间隔（秒） */
    public int cooldownSeconds = 300;

    // ---- 邮箱绑定 ----
    /**
     * 是否强制邮箱绑定/登记：true（默认）时未绑定账号执行 /resetpassword &lt;邮箱&gt; 会被拒绝，
     * 必须先 /resetpassword bind &lt;邮箱&gt; 验证并绑定，或由服主在
     * config/easyauthreset_mailmap.json 中预登记。
     */
    public boolean requireEmailBind = true;

    // ---- 点击激活链接（可选，需开放端口） ----
    /** 是否在绑定验证邮件中附带"点击激活链接"（无需输入验证码即可完成绑定） */
    public boolean enableClickActivation = false;
    /** 绑定 HTTP 服务监听的端口 */
    public int activationHttpPort = 8123;
    /** 绑定 HTTP 服务监听的地址（0.0.0.0 = 对外可达） */
    public String activationHttpBind = "0.0.0.0";
    /**
     * 玩家浏览器可访问的地址（不含尾部斜杠），如 http://play.example.com:8123。
     * 若为空则邮件中不附带链接（仍可用验证码方式）。反代场景可填 https://域名 并把 /easyauthreset/ 代理到该端口。
     */
    public String activationPublicUrl = "";

    // ---- 消息 ----
    /** 语言：zh / en */
    public String language = "zh";
    /** 玩家消息统一前缀（可为空），如 "§e[密码重置]§r " */
    public String messagePrefix = "";

    // ---- 邮件内容 ----
    public String verificationSubject = "【服务器】密码重置验证码";
    public String newPasswordSubject = "【服务器】密码已重置";

    // ---- IP 一致性校验（安全事件告警，可选）----
    /**
     * off=关闭；warn=当前IP与账号历史登录IP不同时<b>仅告警</b>（操作放行）；
     * strict=差异时<b>拦截操作</b>并告警（误杀换IP玩家，默认关闭）。
     * 公网IP相同也判为相同；无法获取IP/无历史记录时一律放行。
     */
    public String ipCheckMode = "off";
    /** 安全事件（IP差异等）管理员告警邮箱；为空则仅游戏内告警（发给OP） */
    public String alertEmail = "";

    public boolean isMailConfigured() {
        return emailSender != null && !emailSender.trim().isEmpty()
                && ((emailPassword != null && !emailPassword.trim().isEmpty())
                || (emailPasswordEnvVar != null && !emailPasswordEnvVar.trim().isEmpty()));
    }

    public static EasyAuthResetConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("easyauthreset.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        EasyAuthResetConfig config = new EasyAuthResetConfig();
        boolean dirty = false;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                dirty = config.apply(obj);
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("读取 easyauthreset.json 失败，使用默认配置", e);
            }
        }

        config.sanitize();
        if (dirty || !Files.exists(path)) {
            config.save();
        }
        return config;
    }

    /**
     * 将 JSON 对象逐字段合并到当前默认值实例。
     * 返回是否发生了缺省值回填（用于触发一次重新写盘）：
     * 任一当前配置字段缺失（旧版配置文件）都会标记为需要重写，保证文件与最新 schema 一致。
     */
    private boolean apply(JsonObject obj) {
        boolean dirty = false;
        for (Field field : EasyAuthResetConfig.class.getFields()) {
            if (!obj.has(field.getName())) {
                dirty = true;
            }
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive()) {
                dirty = true;
                continue;
            }
            try {
                Field field = EasyAuthResetConfig.class.getField(entry.getKey());
                if (field.getType() == String.class) {
                    String v = value.getAsString();
                    if (!v.isEmpty() && ((String) field.get(this)).isBlank()) dirty = true;
                    if (!v.isEmpty()) field.set(this, v);
                } else if (field.getType() == int.class) {
                    field.setInt(this, value.getAsInt());
                } else if (field.getType() == boolean.class) {
                    field.setBoolean(this, value.getAsBoolean());
                }
            } catch (NoSuchFieldException ignored) {
                // 未知字段：忽略
            } catch (Exception e) {
                LOGGER.warn("配置字段 {} 无法解析，使用默认值", entry.getKey(), e);
            }
        }
        return dirty;
    }

    /** 范围校验，防止极端值导致异常（如 0 位验证码、负冷却）。 */
    private void sanitize() {
        if (smtpHost == null || smtpHost.isBlank()) smtpHost = "smtp.gmail.com";
        if (smtpPort < 1 || smtpPort > 65535) smtpPort = 587;
        if (smtpTimeoutMillis < 1000 || smtpTimeoutMillis > 120000) smtpTimeoutMillis = 15000;
        if (smtpRetries < 0 || smtpRetries > 3) smtpRetries = 1;
        if (emailSender == null || emailSender.isBlank()) emailSender = "your-email@gmail.com";
        if (emailPassword == null) emailPassword = "";
        if (emailPasswordEnvVar == null) emailPasswordEnvVar = "";

        if (codeExpirySeconds < 30 || codeExpirySeconds > 86400) codeExpirySeconds = 300;
        if (codeLength < 4 || codeLength > 10) codeLength = 6;
        if (maxCodeAttempts < 1 || maxCodeAttempts > 100) maxCodeAttempts = 5;
        if (tempPasswordLength < 8 || tempPasswordLength > 64) tempPasswordLength = 12;
        if (cooldownSeconds < 10 || cooldownSeconds > 86400) cooldownSeconds = 300;
        if (activationHttpPort < 1024 || activationHttpPort > 65535) activationHttpPort = 8123;
        if (activationHttpBind == null || activationHttpBind.isBlank()) activationHttpBind = "0.0.0.0";
        if (activationPublicUrl == null) activationPublicUrl = "";
        if (activationPublicUrl.endsWith("/")) {
            activationPublicUrl = activationPublicUrl.substring(0, activationPublicUrl.length() - 1);
        }
        if (language == null || !(language.equals("zh") || language.equals("en"))) language = "zh";
        if (messagePrefix == null) messagePrefix = "";
        if (!"off".equals(ipCheckMode) && !"warn".equals(ipCheckMode)
                && !"strict".equals(ipCheckMode)) ipCheckMode = "off";
        if (alertEmail == null) alertEmail = "";
        if (verificationSubject == null || verificationSubject.isBlank())
            verificationSubject = "【服务器】密码重置验证码";
        if (newPasswordSubject == null || newPasswordSubject.isBlank())
            newPasswordSubject = "【服务器】密码已重置";
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("easyauthreset.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = Files.newBufferedWriter(path)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            LOGGER.error("写入 easyauthreset.json 失败", e);
        }
    }
}
