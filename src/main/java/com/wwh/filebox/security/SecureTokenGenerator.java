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
}
