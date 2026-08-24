package xyz.nikitacartes.easyauthreset.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import xyz.nikitacartes.easyauthreset.EasyAuthReset;
import xyz.nikitacartes.easyauthreset.config.EasyAuthResetConfig;
import xyz.nikitacartes.easyauthreset.storage.PlayerEmailStorage;
import xyz.nikitacartes.easyauthreset.verification.VerificationCodeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轻量激活链接 HTTP 服务（JDK 自带 com.sun.net.httpserver，无需额外依赖）。
 *
 * <p>端点：GET {@code /easyauthreset/activate/<token>}（一次性，随绑定验证码过期）。
 * 仅做"绑定激活"一件事：不暴露任何管理功能、不回显用户输入（防注入/防 XSS）。
 * 注意：本服务为裸 HTTP（无 TLS），token 走明文；生产建议暴露在反向代理（HTTPS）之后。</p>
 *
 * <p>开启条件：{@code enableClickActivation=true} 且 {@code activationPublicUrl} 已配置；
 * 监听端口 {@code activationHttpPort} 需要在防火墙/云安全组放行。</p>
 */
public class HttpActivationServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyAuthReset.MOD_ID);
    private static final String CONTEXT_PATH = "/easyauthreset/activate/";

    private final EasyAuthResetConfig config;
    private final VerificationCodeManager codeManager;
    private final PlayerEmailStorage emailStorage;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "easyauthreset-http");
        thread.setDaemon(true);
        return thread;
    });
    private HttpServer server;

    public HttpActivationServer(EasyAuthResetConfig config, VerificationCodeManager codeManager,
                                PlayerEmailStorage emailStorage) {
        this.config = config;
        this.codeManager = codeManager;
        this.emailStorage = emailStorage;
    }

    public boolean isEnabled() {
        return config.enableClickActivation
                && config.activationPublicUrl != null && !config.activationPublicUrl.isEmpty();
    }

    public void start() {
        if (!isEnabled()) {
            if (config.enableClickActivation) {
                LOGGER.error("enableClickActivation=true 但 activationPublicUrl 为空，点击激活不可用（仅验证码方式）");
            }
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(config.activationHttpBind, config.activationHttpPort), 0);
            server.createContext(CONTEXT_PATH, this::handleActivate);
            server.setExecutor(executor);
            server.start();
            LOGGER.info("点击激活服务已启动: http://{}:{}{}<token> (公开地址: {})",
                    config.activationHttpBind, config.activationHttpPort, CONTEXT_PATH, config.activationPublicUrl);
        } catch (IOException e) {
            LOGGER.error("无法启动点击激活 HTTP 服务（端口 {} 被占用或地址非法）", config.activationHttpPort, e);
        }
    }

    private void handleActivate(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respondHtml(exchange, 405, page("405", "Method Not Allowed", "只支持 GET 请求。"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith(CONTEXT_PATH)) {
                respondHtml(exchange, 404, page("404", "Not Found", "链接无效。"));
                return;
            }
            String token = path.substring(CONTEXT_PATH.length()).trim();
            VerificationCodeManager.ActivationOutcome outcome = codeManager.activateByToken(token);

            if (outcome.result() == VerificationCodeManager.ActivateResult.SUCCESS) {
                String email = emailStorage.getBinding(outcome.uuid());
                LOGGER.info("点击激活绑定成功: uuid={}, 邮箱={}", outcome.uuid(), email);
                respondHtml(exchange, 200, page("绑定成功", "✅ 邮箱绑定成功",
                        "该账号的邮箱已验证并绑定，请返回游戏，直接执行 /resetpassword 申请重置密码。"));
            } else {
                respondHtml(exchange, 404, page("链接失效", "❌ 链接无效或已过期",
                        "该激活链接不存在、已使用或已过期。请返回游戏重新执行 /resetpassword bind <邮箱> 获取新链接。"));
            }
        } catch (Exception e) {
            LOGGER.error("处理激活请求异常", e);
            respondHtml(exchange, 500, page("500", "服务器内部错误", "请稍后重试。"));
        } finally {
            exchange.close();
        }
    }

    private static void respondHtml(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String page(String title, String heading, String message) {
        return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" "
                + "content=\"width=device-width,initial-scale=1\"><title>" + escape(title)
                + "</title><style>body{font-family:system-ui,sans-serif;max-width:520px;margin:80px auto;"
                + "padding:0 16px;line-height:1.7;color:#222}h1{font-size:1.3rem}"
                + "a{color:#0b6bce}</style></head><body><h1>" + escape(heading) + "</h1><p>"
                + escape(message) + "</p></body></html>";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public void shutdown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        executor.shutdownNow();
    }
}
