package com.farmland.intel.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 简单的 AES 加解密工具，用于 API Key 等敏感字段的存储加密。
 * 密钥从环境变量 AI_ENCRYPT_KEY 读取，默认使用固定密钥（仅限开发环境）。
 */
public final class CryptoUtils {

    private CryptoUtils() {}

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private static String getKey() {
        String envKey = System.getenv("AI_ENCRYPT_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            return envKey;
        }
        // 开发环境默认密钥（生产环境必须设置 AI_ENCRYPT_KEY）
        return "bang-agro-aes-key-32!"; // 16 字节
    }

    /**
     * 加密字符串
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] keyBytes = getKey().substring(0, 16).getBytes();
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密字符串
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            byte[] keyBytes = getKey().substring(0, 16).getBytes();
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            // 可能是旧版明文数据，直接返回原文
            return cipherText;
        }
    }

    /**
     * 判断字符串是否已加密（Base64 格式）
     */
    public static boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            Base64.getDecoder().decode(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
