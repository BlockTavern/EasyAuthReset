package xyz.nikitacartes.easyauthreset.email;

import java.util.function.Consumer;

/**
 * 邮件服务接口。所有实现必须<b>异步</b>发送（SMTP 可能阻塞数秒，严禁占用服务器主线程），
 * 并在完成后通过 {@code onResult} 回调结果（回调线程不保证是服务器线程，调用方自行切回）。
 */
public interface EmailService {
    /**
     * 发送验证码邮件。
     *
     * @param activationLink 可点击的激活链接（可为 null，则该邮件仅含验证码）
     */
    void sendVerificationCode(String toEmail, String playerName, String code,
                              String activationLink, Consumer<Boolean> onResult);

    void sendNewPassword(String toEmail, String playerName, String newPassword, Consumer<Boolean> onResult);

    /**
     * 发送安全告警邮件给管理员（config.alertEmail）。异步发送，失败仅记日志。
     * alertEmail 未配置时不发送。
     */
    void sendAdminAlert(String subject, String body);

    /**
     * 启动时探测 SMTP 服务器的 TCP 可达性（异步，仅记日志）。
     * 用于快速发现"服务器连不上 smtp.gmail.com 之类"的网络问题。
     */
    void probe();

    /**
     * 按需网络诊断（DNS + 465/587 端口连通性），结果通过回调返回。
     * 供 /easyauthreset diag 管理命令使用。
     */
    void runDiagnostics(java.util.function.Consumer<java.util.List<String>> onResult);

    void shutdown();
}
