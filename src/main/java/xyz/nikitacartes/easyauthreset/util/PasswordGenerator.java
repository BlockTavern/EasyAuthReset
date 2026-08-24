package xyz.nikitacartes.easyauthreset.util;

import java.security.SecureRandom;

/**
 * 安全随机密码生成器（SecureRandom）。
 * 字符集不含空格与引号，可直接在聊天栏打字输入。
 */
public final class PasswordGenerator {
    private static final String ALLOWED_CHARS =
            "abcdefghijklmnopqrstuvwxyz"
                    + "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    + "0123456789"
                    + "!@#$%^&*()_+-=";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALLOWED_CHARS.charAt(RANDOM.nextInt(ALLOWED_CHARS.length())));
        }
        return sb.toString();
    }
}
