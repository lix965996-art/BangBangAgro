package com.farmland.intel.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;

@Component
@Slf4j
public class TokenUtils {

    private static IUserService staticUserService;
    private static String staticJwtSecret;
    private static int staticExpireHours;

    @Resource
    private IUserService userService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expire-hours:12}")
    private Integer expireHours;

    @PostConstruct
    public void init() {
        staticUserService = userService;
        staticJwtSecret = jwtSecret;
        staticExpireHours = expireHours == null ? 12 : expireHours;

        if (StrUtil.isBlank(staticJwtSecret) || staticJwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT 密钥未配置或长度不足（至少 32 字符）。请设置环境变量 JWT_SECRET。");
        }
    }

    public static String genToken(String userId) {
        return JWT.create()
                .withSubject(userId)
                .withIssuedAt(new Date())
                .withExpiresAt(DateUtil.offsetHour(new Date(), staticExpireHours))
                .sign(Algorithm.HMAC256(staticJwtSecret));
    }

    public static JWTVerifier getVerifier() {
        return JWT.require(Algorithm.HMAC256(staticJwtSecret)).build();
    }

    public static User getCurrentUser() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String token = request.getHeader("token");
            if (StrUtil.isNotBlank(token)) {
                // 使用 verify 验证签名和过期时间，而非仅 decode
                String userId = getVerifier().verify(token).getSubject();
                return staticUserService.getById(Integer.valueOf(userId));
            }
        } catch (Exception e) {
            log.error("Token verification failed: {}", e.getMessage());
            return null;
        }
        return null;
    }

    /**
     * 验证WebSocket连接的Token与用户名是否匹配
     */
    public static boolean validateWebSocketToken(String token, String username) {
        try {
            String userId = getVerifier().verify(token).getSubject();
            User user = staticUserService.getById(Integer.valueOf(userId));
            return user != null && username.equals(user.getUsername());
        } catch (Exception e) {
            return false;
        }
    }
}
