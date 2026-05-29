package com.farmland.intel.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;

public final class PasswordUtils {

    private PasswordUtils() {
    }

    public static String encode(String rawPassword) {
        if (StrUtil.isBlank(rawPassword)) {
            return rawPassword;
        }

        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        if (StrUtil.isBlank(rawPassword) || StrUtil.isBlank(storedPassword)) {
            return false;
        }
        if (!isEncoded(storedPassword)) {
            // 明文密码不安全，拒绝匹配并记录警告
            System.err.println("[安全警告] 数据库中发现未编码的明文密码，请立即执行密码迁移！");
            return false;
        }
        return BCrypt.checkpw(rawPassword, storedPassword);
    }

    public static boolean needsEncoding(String password) {
        return StrUtil.isNotBlank(password) && !isEncoded(password);
    }

    public static boolean isEncoded(String password) {
        return StrUtil.startWithAny(password, "$2a$", "$2b$", "$2y$");
    }
}
