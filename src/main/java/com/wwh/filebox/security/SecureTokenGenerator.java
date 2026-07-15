package com.wwh.filebox.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secure token generator
 * 安全令牌生成器
 */
public class SecureTokenGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int TOKEN_LENGTH = 32; // bytes

    // 仅字母(大小写)+数字,方便在控制台复制(无特殊字符) / alphanumeric only, easy console copy, no symbols
    private static final char[] ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /**
     * Generate a cryptographically secure random token
     * 生成加密安全的随机令牌
     */
    public static String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Generate API key token (longer for additional security)
     * 生成API密钥令牌（更长以提供额外安全性）
     */
    public static String generateApiKeyToken() {
        byte[] tokenBytes = new byte[48]; // 48 bytes for API keys
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * 生成指定长度的随机字母数字字符串(A–Z a–z 0–9),用于首启/重置的管理员密码。
     * Generate a random alphanumeric string of the given length for admin passwords.
     */
    public static String generateAlphanumeric(int length) {
        char[] out = new char[length];
        for (int i = 0; i < length; i++) {
            out[i] = ALPHANUMERIC[secureRandom.nextInt(ALPHANUMERIC.length)];
        }
        return new String(out);
    }
}
