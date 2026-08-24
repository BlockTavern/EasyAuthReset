package xyz.nikitacartes.easyauthreset.email;

import xyz.nikitacartes.easyauthreset.EasyAuthReset;
import xyz.nikitacartes.easyauthreset.config.EasyAuthResetConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Gmail SMTP 实现（也兼容任意支持 STARTTLS/SSL 的 SMTP 服务器）。
 *
 * <p>使用 {@code javax.mail 1.6.2}（JavaMail），已通过 {@code include} 打进模组 jar，
 * 生产环境无需额外放置任何 jar。</p>
 *
 * <p>安全增强：SMTP 密码优先从环境变量读取（{@code emailPasswordEnvVar}），
 * 避免在配置文件中明文保存；发送失败会按 {@code smtpRetries} 自动重试。</p>
 */
public class GmailEmailService implements EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyAuthReset.MOD_ID);

    private final Session session;
    private final String sender;
    private final EasyAuthResetConfig config;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "easyauthreset-mail-sender");
        thread.setDaemon(true);
        return thread;
    });

    public GmailEmailService(EasyAuthResetConfig config) {
        this.config = config;
        this.sender = config.emailSender;

        String password = resolvePassword();

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", config.smtpHost);
        props.put("mail.smtp.port", String.valueOf(config.smtpPort));
        props.put("mail.smtp.connectiontimeout", String.valueOf(config.smtpTimeoutMillis));
        props.put("mail.smtp.timeout", String.valueOf(config.smtpTimeoutMillis));
        props.put("mail.smtp.writetimeout", String.valueOf(config.smtpTimeoutMillis));
        if (config.smtpSsl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", config.smtpHost);
        } else {
            props.put("mail.smtp.starttls.enable", String.valueOf(config.smtpTls));
            if (config.smtpTls) {
                props.put("mail.smtp.starttls.required", "true");
            }
        }

        this.session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.emailSender, password);
            }
        });
    }

    /** 优先环境变量，其次配置文件。 */
    private String resolvePassword() {
        String envVar = config.emailPasswordEnvVar == null ? "" : config.emailPasswordEnvVar.trim();
        if (!envVar.isEmpty()) {
            String value = System.getenv(envVar);
            if (value != null && !value.isEmpty()) {
                return value;
            }
            LOGGER.warn("Env var {} is unset or empty; falling back to emailPassword in config", envVar);
        }
        return config.emailPassword == null ? "" : config.emailPassword;
    }

    @Override
    public void sendVerificationCode(String toEmail, String playerName, String code,
                                     String activationLink, Consumer<Boolean> onResult) {
        StringBuilder body = new StringBuilder()
                .append("尊敬的 ").append(playerName).append("，您好！\n\n")
                .append("您正在申请重置游戏密码（或绑定邮箱）。本次验证码为：\n\n")
                .append("    ").append(code).append("\n\n");
        if (activationLink != null && !activationLink.isEmpty()) {
            body.append("您也可以直接点击以下链接完成验证（链接与验证码任一有效）：\n\n")
                    .append("    ").append(activationLink).append("\n\n");
        }
        body.append("验证码有效期为 ").append(config.codeExpirySeconds).append(" 秒，请勿泄露给他人。\n")
                .append("如非本人操作，请忽略此邮件。\n\n")
                .append("—— 服务器管理组");
        send(toEmail, config.verificationSubject, body.toString(), onResult);
    }

    @Override
    public void sendNewPassword(String toEmail, String playerName, String newPassword, Consumer<Boolean> onResult) {
        String body = "尊敬的 " + playerName + "，您好！\n\n"
                + "您的游戏密码已成功重置。临时密码为：\n\n"
                + "    " + newPassword + "\n\n"
                + "登录后请立即使用以下指令修改密码：\n"
                + "    /account changePassword " + newPassword + " 你的新密码\n\n"
                + "如非本人操作，请立即联系管理员。\n\n"
                + "—— 服务器管理组";
        send(toEmail, config.newPasswordSubject, body, onResult);
    }

    @Override
    public void sendAdminAlert(String subject, String body) {
        if (config.alertEmail == null || config.alertEmail.isBlank()) {
            LOGGER.info("Security alert email not sent (alertEmail not configured): {}", subject);
            return;
        }
        send(config.alertEmail.trim(), subject, body, null);
    }

    private void send(String to, String subject, String body, Consumer<Boolean> onResult) {
        executor.submit(() -> {
            boolean ok = false;
            int attempts = 1 + Math.max(0, config.smtpRetries);
            for (int i = 1; i <= attempts && !ok; i++) {
                try {
                    MimeMessage msg = new MimeMessage(session);
                    msg.setFrom(new InternetAddress(sender));
                    if (config.emailReplyTo != null && !config.emailReplyTo.isBlank()) {
                        msg.setReplyTo(new javax.mail.Address[]{new InternetAddress(config.emailReplyTo.trim())});
                    }
                    msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
                    msg.setSubject(subject, "UTF-8");
                    msg.setText(body, "UTF-8");
                    Transport.send(msg);
                    ok = true;
                } catch (MessagingException e) {
                    ok = false;
                    if (i < attempts) {
                        LOGGER.warn("Mail send failed (to={}), attempt {}/{}; retrying in 3s", to, i, attempts, e);
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        LOGGER.error("Mail send failed (to={}) after {} attempts "
                                + "(host={}:{}, sender={}). Hint: smtpHost must match the sender's own provider "
                                + "(Gmail SMTP only sends Gmail mails and is unreachable from mainland China; "
                                + "QQ mailbox: smtp.qq.com:465 + SSL, Tencent enterprise: smtp.exmail.qq.com:465 + SSL, "
                                + "NetEase: smtp.ym.163.com:465 + SSL).", to, attempts,
                                config.smtpHost, config.smtpPort, sender, e);
                    }
                } catch (Exception e) {
                    ok = false;
                    LOGGER.error("Mail send failed (to={})", to, e);
                }
            }
            final boolean sent = ok;
            if (onResult != null) {
                onResult.accept(sent);
            }
        });
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }
}
