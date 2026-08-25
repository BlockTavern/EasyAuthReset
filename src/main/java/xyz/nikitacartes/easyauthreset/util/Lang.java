package xyz.nikitacartes.easyauthreset.util;

import xyz.nikitacartes.easyauthreset.config.EasyAuthResetConfig;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * 轻量双语文案（zh / en）+ 统一消息前缀。
 */
public final class Lang {
    private static final Map<String, Map<String, String>> TABLES = new HashMap<>();

    static {
        Map<String, String> zh = new HashMap<>();
        zh.put("usage", "用法：/resetpassword <邮箱> | /resetpassword confirm <验证码>\n"
                + "       /resetpassword bind <邮箱> | /resetpassword bind confirm <验证码>");
        zh.put("onlyPlayers", "此命令只能由玩家执行");
        zh.put("sendingCode", "正在发送验证码至 {0}，请稍候…");
        zh.put("sendingCodeBound", "该账号已绑定邮箱，正在向绑定邮箱 {0} 发送验证码，请稍候…");
        zh.put("codeSent", "验证码已发送至 {0}。\n若等待期间被踢出，请重新连接后直接输入 /resetpassword confirm <验证码> 完成重置。");
        zh.put("codeSentOwner", "验证码已发送至你登记的邮箱 {0}。\n若等待期间被踢出，请重新连接后直接输入 /resetpassword confirm <验证码> 完成重置。");
        zh.put("bindCodeSent", "绑定验证码已发送至 {0}。\n输入 /resetpassword bind confirm <验证码> 完成绑定，之后重置将只发往该邮箱。");
        zh.put("bindCodeSentLink", "绑定验证邮件已发送至 {0}（内含一次性激活链接）。\n点击邮件链接，或输入 /resetpassword bind confirm <验证码> 完成绑定。");
        zh.put("bindRebindCodeSent", "将更换绑定邮箱：验证码已发送至新邮箱 {0}。\n输入 /resetpassword bind confirm <验证码> 完成更换（无需联系管理员）。");
        zh.put("bindRebindCodeSentLink", "将更换绑定邮箱：验证邮件已发送至新邮箱 {0}（内含一次性激活链接）。\n点击邮件链接，或输入 /resetpassword bind confirm <验证码> 完成更换（无需联系管理员）。");
        zh.put("codeSendFailed", "验证码邮件发送失败，请检查邮箱地址或稍后重试（已进入冷却）。");
        zh.put("cooldown", "操作过于频繁，请等待 {0} 秒后再试。");
        zh.put("notRegistered", "该账号未在服务器注册，无法重置密码。");
        zh.put("notRegisteredBind", "仅已注册玩家可验证并绑定邮箱。");
        zh.put("invalidEmail", "邮箱格式无效，请检查后重试。");
        zh.put("dbNotReady", "EasyAuth 数据库未就绪，请稍后再试。");
        zh.put("codeInvalid", "验证码错误或已过期，请重新申请。");
        zh.put("codeAttemptsExceeded", "验证码错误次数过多，已作废，请重新申请。");
        zh.put("accountGone", "无法确认你的注册账号（账号可能已注销）。");
        zh.put("resetSuccess", "密码已重置！临时密码：{0}\n登录：/login {0}\n登录后立即修改：/account changePassword {0} <你的新密码>");
        zh.put("resetMailSent", "新密码邮件已发送至 {0}。");
        zh.put("resetMailFailed", "密码已重置，但邮件发送失败！临时密码：{0} 请立即截图保存并联系管理员。");
        zh.put("bindRequired", "该账号尚未绑定邮箱，请先执行 /resetpassword bind <你的邮箱> 验证并绑定。");
        zh.put("bindDone", "绑定成功！该账号的邮箱为 {0}，之后重置密码只会发送到该邮箱。现在可直接输入 /resetpassword 申请重置。");
        zh.put("bindConfirmInvalid", "绑定验证码错误或已过期，请重新执行 /resetpassword bind <邮箱>。");
        zh.put("bindNotNeeded", "该账号已由服主登记邮箱，无需自助绑定。");
        zh.put("internalError", "内部错误，请稍后再试。");
        zh.put("ipMismatchBlocked", "安全保护：检测到当前网络环境与账号历史登录 IP 不同，本次操作已暂停并向管理员告警。如有疑问请联系管理员。");
        zh.put("ipMismatchWarn", "安全提示：当前网络环境与账号历史登录 IP 不同，本次操作已放行并向管理员告警。如有疑问请联系管理员。");
        zh.put("adminAlertIp", "⚠️ EasyAuthReset 安全告警：{0} 玩家 {1} (uuid={2}) 当前IP={3} 历史IP={4} 操作={5}");

        Map<String, String> en = new HashMap<>();
        en.put("usage", "Usage: /resetpassword <email> | /resetpassword confirm <code>\n"
                + "       /resetpassword bind <email> | /resetpassword bind confirm <code>");
        en.put("onlyPlayers", "This command can only be used by players");
        en.put("sendingCode", "Sending verification code to {0}, please wait…");
        en.put("sendingCodeBound", "Account has a bound email — sending verification code to {0}, please wait…");
        en.put("codeSent", "Verification code sent to {0}.\nIf you were kicked while waiting, reconnect and run /resetpassword confirm <code>.");
        en.put("codeSentOwner", "Verification code sent to your registered email {0}.\nIf you were kicked while waiting, reconnect and run /resetpassword confirm <code>.");
        en.put("bindCodeSent", "Binding verification code sent to {0}.\nRun /resetpassword bind confirm <code> to finish; future resets will only go to this email.");
        en.put("bindCodeSentLink", "Binding email sent to {0} (contains a one-time activation link).\nClick the link, or run /resetpassword bind confirm <code> to finish.");
        en.put("bindRebindCodeSent", "Changing bound email: verification code sent to {0}.\nRun /resetpassword bind confirm <code> to finish (no admin needed).");
        en.put("bindRebindCodeSentLink", "Changing bound email: email sent to {0} (contains a one-time activation link).\nClick the link, or run /resetpassword bind confirm <code> to finish (no admin needed).");
        en.put("codeSendFailed", "Failed to send the code email. Check the address or try again later (cooldown applied).");
        en.put("cooldown", "Too many requests. Try again in {0} seconds.");
        en.put("notRegistered", "This account is not registered on the server.");
        en.put("notRegisteredBind", "Only registered players can verify and bind an email.");
        en.put("invalidEmail", "Invalid email format.");
        en.put("dbNotReady", "EasyAuth database is not ready. Try again later.");
        en.put("codeInvalid", "Wrong or expired code. Please request a new one.");
        en.put("codeAttemptsExceeded", "Too many wrong attempts, code invalidated. Request a new one.");
        en.put("accountGone", "Could not verify your registered account (maybe it was removed).");
        en.put("resetSuccess", "Password reset! Temporary password: {0}\nLogin: /login {0}\nThen change it: /account changePassword {0} <your new password>");
        en.put("resetMailSent", "New password email sent to {0}.");
        en.put("resetMailFailed", "Password has been reset but the email failed! Temporary password: {0} Save it now and contact an admin.");
        en.put("bindRequired", "This account has no bound email. First run /resetpassword bind <your email>.");
        en.put("bindDone", "Bound successfully! Reset emails for this account will only go to {0}. You can now run /resetpassword.");
        en.put("bindConfirmInvalid", "Binding code wrong or expired. Run /resetpassword bind <email> again.");
        en.put("bindNotNeeded", "This account has an admin-configured email; no need to bind.");
        en.put("internalError", "Internal error. Try again later.");
        en.put("ipMismatchBlocked", "Security protection: your current IP differs from the account's last login IP. Action blocked and admins alerted.");
        en.put("ipMismatchWarn", "Security notice: your current IP differs from the account's last login IP. Action allowed and admins alerted.");
        en.put("adminAlertIp", "⚠️ EasyAuthReset SECURITY ALERT: {0} player {1} (uuid={2}) currentIp={3} lastIp={4} action={5}");

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
