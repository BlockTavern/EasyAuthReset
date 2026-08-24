package xyz.nikitacartes.easyauthreset;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import xyz.nikitacartes.easyauthreset.command.ResetPasswordCommand;
import xyz.nikitacartes.easyauthreset.config.EasyAuthResetConfig;
import xyz.nikitacartes.easyauthreset.email.EmailService;
import xyz.nikitacartes.easyauthreset.email.GmailEmailService;
import xyz.nikitacartes.easyauthreset.handler.PasswordResetHandler;
import xyz.nikitacartes.easyauthreset.storage.PlayerEmailStorage;
import xyz.nikitacartes.easyauthreset.storage.StateStorage;
import xyz.nikitacartes.easyauthreset.verification.VerificationCodeManager;
import xyz.nikitacartes.easyauthreset.web.HttpActivationServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EasyAuthReset 主类。
 *
 * <p>设计要点（与 EasyAuth 3.4.x 真实 API 对齐）：</p>
 * <ul>
 *   <li>EasyAuth 没有实例单例，数据库访问是 {@code EasyAuth.DB}（静态 {@code DbApi} 字段）；</li>
 *   <li>数据以<b>玩家名</b>（而非 UUID）为键，注册状态 = {@code PlayerEntryV1.password} 非空；</li>
 *   <li>本模组仅将连接 UUID 用于自身的内存映射（冷却、验证码、邮箱），与 EasyAuth 数据库无关。</li>
 * </ul>
 */
public class EasyAuthReset implements ModInitializer {
    public static final String MOD_ID = "easyauthreset";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static EasyAuthReset INSTANCE;

    private EasyAuthResetConfig config;
    private EmailService emailService;
    private VerificationCodeManager codeManager;
    private PlayerEmailStorage emailStorage;
    private StateStorage stateStorage;
    private PasswordResetHandler resetHandler;
    private HttpActivationServer activationServer;

    @Override
    public void onInitialize() {
        INSTANCE = this;

        // 1. 配置（不存在则生成默认文件；字段缺失时自动补齐默认值）
        config = EasyAuthResetConfig.load();

        // 2. 邮件服务
        emailService = new GmailEmailService(config);

        // 3. 易失状态持久化（验证码 + 冷却，重启不丢）
        stateStorage = new StateStorage();

        // 4. 验证码管理器（内存 + 文件存储，过期自动清理；支持绑定激活 token）
        codeManager = new VerificationCodeManager(config.codeExpirySeconds, config.codeLength,
                config.maxCodeAttempts, stateStorage);

        // 5. UUID → 邮箱映射 + 绑定邮箱 + 服主登记邮箱持久化
        emailStorage = new PlayerEmailStorage();

        // 6. 核心业务逻辑（单例：冷却表必须跨指令执行保持）
        resetHandler = new PasswordResetHandler(config, emailService, codeManager, emailStorage, stateStorage);

        // 7. 点击激活链接 HTTP 服务（可选）
        activationServer = new HttpActivationServer(config, codeManager, emailStorage);
        activationServer.start();

        // 8. 注册指令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ResetPasswordCommand.register(dispatcher)
        );

        // 9. 服务器停止时保存状态并释放资源
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            activationServer.shutdown();
            stateStorage.shutdown();
            codeManager.shutdown();
            emailService.shutdown();
        });

        if (!config.isMailConfigured()) {
            LOGGER.warn("SMTP is not configured (sender/app-password or env var empty). Fill config/easyauthreset.json and restart the server!");
        }
        LOGGER.info("EasyAuthReset initialized (EasyAuth 3.3.5+/3.4.x API).");
    }

    public static EasyAuthReset getInstance() {
        return INSTANCE;
    }

    public EasyAuthResetConfig getConfig() {
        return config;
    }

    public EmailService getEmailService() {
        return emailService;
    }

    public VerificationCodeManager getCodeManager() {
        return codeManager;
    }

    public PlayerEmailStorage getEmailStorage() {
        return emailStorage;
    }

    public StateStorage getStateStorage() {
        return stateStorage;
    }

    public PasswordResetHandler getResetHandler() {
        return resetHandler;
    }
}
