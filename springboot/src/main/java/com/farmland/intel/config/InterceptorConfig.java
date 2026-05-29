package com.farmland.intel.config;

import com.farmland.intel.config.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Resource
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                // 用户认证相关（必须公开）
                .excludePathPatterns("/user/login", "/user/register", "/user/reset")
                // Swagger文档
                .excludePathPatterns("/swagger**/**", "/webjars/**", "/v3/**", "/doc.html")
                // 健康检查（运维需要，仅开放 /health，actuator 其他端点需认证）
                .excludePathPatterns("/health");
        // 注意：所有业务接口（inventory、purchase、sales、aether、api/chat等）现在都需要登录认证
        // 如果某些接口需要公开访问，请在对应的 Controller 方法上添加 @AuthAccess 注解
    }
}
