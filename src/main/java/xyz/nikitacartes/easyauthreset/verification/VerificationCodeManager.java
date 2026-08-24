package xyz.nikitacartes.easyauthreset.verification;

import xyz.nikitacartes.easyauthreset.storage.StateStorage;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 验证码/激活 token 管理器（内存 + 文件持久化）。
 *
 * <p>特性：按玩家 UUID 绑定、区分用途（重置/绑定）、一次性使用、过期自动清理、
 * 错误尝试次数上限、服务器重启后仍有效（直至过期）。
 * 绑定类验证码可附带一次性<b>激活链接 token</b>（"点击邮件链接直接激活"）。</p>
 */
public class VerificationCodeManager {
    public enum Purpose {
        /** 密码重置流程 */
        RESET("reset"),
        /** 邮箱绑定流程 */
        BIND("bind");

        private final String key;

        Purpose(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        static Purpose fromKey(String key) {
            return "bind".equals(key) ? BIND : RESET;
        }
    }

    public enum VerifyResult {
        /** 验证通过（验证码已消费） */
        SUCCESS,
        /** 验证码不存在 / 错误 / 已过期 / 用途不符 */
        INVALID,
        /** 错误尝试次数超过上限，验证码已作废 */
        ATTEMPTS_EXCEEDED
    }

    /** 点击激活的结果。 */
    public enum ActivateResult {
        SUCCESS,
        /** token 不存在/已使用/已过期 */
        INVALID
    }

    /** 点击激活的完整结果。 */
    public record ActivationOutcome(ActivateResult result, String uuid) {
    }

    private static final class CodeRecord {
        final String code;
        final long expiry;
        final Purpose purpose;
        String token;
        int attempts;

        CodeRecord(String code, long expiry, Purpose purpose) {
            this.code = code;
            this.expiry = expiry;
            this.purpose = purpose;
        }
    }

    private final Map<String, CodeRecord> store = new ConcurrentHashMap<>();
    /** token → uuid 反查索引（点击激活用） */
    private final Map<String, String> tokenIndex = new ConcurrentHashMap<>();
    private final StateStorage state;
    private final long expiryMillis;
    private final int codeLength;
    private final int maxAttempts;
    private final SecureRandom random = new SecureRandom();
    private final ScheduledExecutorService cleaner;

    public VerificationCodeManager(long expirySeconds, int codeLength, int maxAttempts, StateStorage state) {
        this.expiryMillis = expirySeconds * 1000L;
        this.codeLength = codeLength;
        this.maxAttempts = maxAttempts;
        this.state = state;

        // 从磁盘恢复未过期的验证码
        state.getCodes().forEach((uuid, entry) -> {
            CodeRecord record = new CodeRecord(entry.code, entry.expiry, Purpose.fromKey(entry.purpose));
            record.attempts = entry.attempts;
            record.token = entry.token != null && !entry.token.isEmpty() ? entry.token : null;
            store.put(uuid, record);
            if (record.token != null) {
                tokenIndex.put(record.token, uuid);
            }
        });

        this.cleaner = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "easyauthreset-code-cleaner");
            thread.setDaemon(true);
            return thread;
        });
        long period = Math.max(expiryMillis / 2, 60_000L);
        cleaner.scheduleAtFixedRate(this::purgeExpired, period, period, TimeUnit.MILLISECONDS);
    }

    /**
     * 生成并存储验证码，返回验证码明文。
     */
    public String generateAndStore(String uuid, Purpose purpose) {
        long bound = (long) Math.pow(10, codeLength);
        String code = String.format(Locale.ROOT, "%0" + codeLength + "d", random.nextLong(bound));
        CodeRecord record = new CodeRecord(code, System.currentTimeMillis() + expiryMillis, purpose);
        store.put(uuid, record);
        persist(uuid, record);
        return code;
    }

    /**
     * 为指定 UUID 的<b>绑定</b>验证码生成一次性激活链接 token（幂等：已有返回原 token）。
     *
     * @return token；uuid 无有效绑定验证码时返回 null
     */
    public String createActivationToken(String uuid) {
        CodeRecord record = store.get(uuid);
        if (record == null || record.purpose != Purpose.BIND || record.token != null) {
            return record == null ? null : record.token;
        }
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes); // 40 hex chars
        record.token = token;
        tokenIndex.put(token, uuid);
        persist(uuid, record);
        return token;
    }

    /** 获取当前绑定的激活 token（无则 null）。 */
    public String getActivationToken(String uuid) {
        CodeRecord record = store.get(uuid);
        return record == null ? null : record.token;
    }

    /**
     * 验证验证码（一次性，校验用途）。
     */
    public VerifyResult verify(String uuid, Purpose purpose, String inputCode) {
        CodeRecord record = store.get(uuid);
        if (record == null) {
            return VerifyResult.INVALID;
        }
        if (System.currentTimeMillis() > record.expiry) {
            invalidate(uuid);
            return VerifyResult.INVALID;
        }
        if (record.purpose != purpose) {
            return VerifyResult.INVALID;
        }
        if (record.code.equals(inputCode)) {
            invalidate(uuid);
            return VerifyResult.SUCCESS;
        }
        record.attempts++;
        if (record.attempts >= maxAttempts) {
            invalidate(uuid);
            return VerifyResult.ATTEMPTS_EXCEEDED;
        }
        persist(uuid, record);
        return VerifyResult.INVALID;
    }

    /**
     * 点击激活链接：按 token 找到绑定验证码并消费。
     */
    public ActivationOutcome activateByToken(String token) {
        if (token == null || token.isEmpty()) {
            return new ActivationOutcome(ActivateResult.INVALID, null);
        }
        String uuid = tokenIndex.get(token);
        if (uuid == null) {
            return new ActivationOutcome(ActivateResult.INVALID, null);
        }
        CodeRecord record = store.get(uuid);
        if (record == null || record.purpose != Purpose.BIND
                || System.currentTimeMillis() > record.expiry
                || !token.equals(record.token)) {
            invalidate(uuid);
            return new ActivationOutcome(ActivateResult.INVALID, null);
        }
        invalidate(uuid); // 消费（含 token 索引清理）
        return new ActivationOutcome(ActivateResult.SUCCESS, uuid);
    }

    /** 手动失效（如邮件发送失败时）。 */
    public void invalidate(String uuid) {
        CodeRecord record = store.remove(uuid);
        if (record != null && record.token != null) {
            tokenIndex.remove(record.token);
        }
        state.removeCode(uuid);
    }

    private void persist(String uuid, CodeRecord record) {
        StateStorage.CodeEntry entry = new StateStorage.CodeEntry();
        entry.code = record.code;
        entry.expiry = record.expiry;
        entry.attempts = record.attempts;
        entry.purpose = record.purpose.getKey();
        entry.token = record.token;
        state.putCode(uuid, entry);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(entry -> {
            boolean expired = now > entry.getValue().expiry;
            if (expired && entry.getValue().token != null) {
                tokenIndex.remove(entry.getValue().token);
            }
            return expired;
        });
        // 磁盘侧同步清理
        state.getCodes().keySet().removeIf(uuid -> {
            StateStorage.CodeEntry e = state.getCode(uuid);
            return e == null || e.expiry <= now;
        });
        state.save();
    }

    public void shutdown() {
        cleaner.shutdownNow();
        state.save();
    }
}
