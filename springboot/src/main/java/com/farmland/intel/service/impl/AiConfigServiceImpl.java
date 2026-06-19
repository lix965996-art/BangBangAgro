package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.AiConfig;
import com.farmland.intel.mapper.AiConfigMapper;
import com.farmland.intel.service.IAiConfigService;
import com.farmland.intel.utils.CryptoUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AiConfigServiceImpl extends ServiceImpl<AiConfigMapper, AiConfig> implements IAiConfigService {

    /** 各提供商预设 BaseURL */
    public static String defaultBaseUrl(String provider) {
        if (provider == null) return "";
        switch (provider) {
            case "qwen":     return "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "deepseek": return "https://api.deepseek.com/v1";
            case "glm":      return "https://open.bigmodel.cn/api/paas/v4";
            case "minimax":  return "https://api.minimax.chat/v1";
            case "openai":   return "https://api.openai.com/v1";
            default:         return "";
        }
    }

    /** 各提供商推荐默认模型 */
    public static String defaultModel(String provider) {
        if (provider == null) return "qwen-max";
        switch (provider) {
            case "qwen":     return "qwen-max";
            case "deepseek": return "deepseek-chat";
            case "glm":      return "glm-4";
            case "minimax":  return "abab6.5s-chat";
            case "openai":   return "gpt-4o-mini";
            default:         return "gpt-3.5-turbo";
        }
    }

    @Override
    public AiConfig getByUserId(Integer userId) {
        if (userId == null) return buildDefault();
        AiConfig cfg = getOne(new QueryWrapper<AiConfig>().eq("user_id", userId));
        if (cfg == null) return buildDefault();
        // 解密 API Key（兼容旧版明文）
        if (cfg.getApiKey() != null && !cfg.getApiKey().isEmpty()) {
            cfg.setApiKey(CryptoUtils.decrypt(cfg.getApiKey()));
        }
        // 解密视觉模型 Key
        if (cfg.getVisionApiKey() != null && !cfg.getVisionApiKey().isEmpty()) {
            cfg.setVisionApiKey(CryptoUtils.decrypt(cfg.getVisionApiKey()));
        }
        return cfg;
    }

    @Override
    public AiConfig saveOrUpdateByUserId(AiConfig config) {
        if (config == null || config.getUserId() == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        // 补全缺省字段
        if (config.getProvider() == null) config.setProvider("qwen");
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            config.setBaseUrl(defaultBaseUrl(config.getProvider()));
        }
        if (config.getModelName() == null || config.getModelName().isBlank()) {
            config.setModelName(defaultModel(config.getProvider()));
        }
        if (config.getTemperature() == null) {
            config.setTemperature(new BigDecimal("0.42"));
        }
        if (config.getEnabled() == null) config.setEnabled(1);

        // 加密 API Key 后存储
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            config.setApiKey(CryptoUtils.encrypt(config.getApiKey()));
        }
        // 加密视觉模型 Key 后存储
        if (config.getVisionApiKey() != null && !config.getVisionApiKey().isEmpty()) {
            config.setVisionApiKey(CryptoUtils.encrypt(config.getVisionApiKey()));
        }

        // 查找已有记录
        AiConfig existing = getOne(new QueryWrapper<AiConfig>().eq("user_id", config.getUserId()));
        if (existing != null) {
            config.setId(existing.getId());
        }
        saveOrUpdate(config);
        return config;
    }

    private AiConfig buildDefault() {
        AiConfig cfg = new AiConfig();
        cfg.setProvider("qwen");
        cfg.setBaseUrl(defaultBaseUrl("qwen"));
        cfg.setApiKey("");
        cfg.setModelName("qwen-max");
        cfg.setTemperature(new BigDecimal("0.42"));
        cfg.setEnabled(1);
        return cfg;
    }
}
