package com.farmland.intel.controller;

import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.AiConfig;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IAiConfigService;
import com.farmland.intel.service.impl.AiConfigServiceImpl;
import com.farmland.intel.utils.TokenUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 模型配置接口
 * GET  /ai-config          → 读取当前用户配置
 * POST /ai-config          → 保存当前用户配置
 * GET  /ai-config/presets  → 返回所有提供商预设（供前端下拉）
 * POST /ai-config/test     → 测试连通性（发一条 ping 请求）
 */
@RestController
@RequestMapping("/ai-config")
public class AiConfigController {

    @Autowired
    private IAiConfigService aiConfigService;

    @Autowired
    private com.farmland.intel.service.ChatModelFactory chatModelFactory;

    @GetMapping
    public Result getConfig() {
        User user = TokenUtils.getCurrentUser();
        if (user == null) return Result.error(Constants.CODE_401, "未登录");
        AiConfig cfg = aiConfigService.getByUserId(user.getId());
        // 返回前脱敏 apiKey，只保留前4位
        cfg = maskKey(cfg);
        return Result.success(cfg);
    }

    @PostMapping
    public Result saveConfig(@RequestBody AiConfig config) {
        User user = TokenUtils.getCurrentUser();
        if (user == null) return Result.error(Constants.CODE_401, "未登录");

        // 如果前端传的是脱敏 Key（含 ***），则从 DB 取原始 Key
        boolean needFetchExisting = (config.getApiKey() != null && config.getApiKey().contains("***"))
                || (config.getChatApiKey() != null && config.getChatApiKey().contains("***"))
                || (config.getVisionApiKey() != null && config.getVisionApiKey().contains("***"));
        if (needFetchExisting) {
            AiConfig existing = aiConfigService.getByUserId(user.getId());
            if (config.getApiKey() != null && config.getApiKey().contains("***")) {
                config.setApiKey(existing.getApiKey());
            }
            if (config.getChatApiKey() != null && config.getChatApiKey().contains("***")) {
                config.setChatApiKey(existing.getChatApiKey());
            }
            if (config.getVisionApiKey() != null && config.getVisionApiKey().contains("***")) {
                config.setVisionApiKey(existing.getVisionApiKey());
            }
        }

        config.setUserId(user.getId());
        aiConfigService.saveOrUpdateByUserId(config);
        // 配置变更后失效该用户的旧 ChatClient 缓存，确保新 key/baseUrl 立即生效
        chatModelFactory.evictCache(user.getId());
        return Result.success(maskKey(config));
    }

    /** 返回各提供商预设 baseUrl + 推荐 model */
    @GetMapping("/presets")
    public Result getPresets() {
        Map<String, Object> presets = new HashMap<>();
        for (String p : new String[]{"qwen", "deepseek", "glm", "minimax", "openai", "custom"}) {
            Map<String, String> info = new HashMap<>();
            info.put("baseUrl", AiConfigServiceImpl.defaultBaseUrl(p));
            info.put("model",   AiConfigServiceImpl.defaultModel(p));
            presets.put(p, info);
        }
        return Result.success(presets);
    }

    /**
     * 连通性测试：用当前配置发一条最小 chat 请求，
     * 只看是否返回 200 + choices，不关心内容。
     */
    @PostMapping("/test")
    public Result testConnection(@RequestBody(required = false) AiConfig testCfg) {
        User user = TokenUtils.getCurrentUser();
        if (user == null) return Result.error(Constants.CODE_401, "未登录");

        AiConfig cfg = (testCfg != null && testCfg.getApiKey() != null
                        && !testCfg.getApiKey().contains("***"))
                ? testCfg
                : aiConfigService.getByUserId(user.getId());

        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            return Result.error(Constants.CODE_400, "请先填写 API Key");
        }
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            return Result.error(Constants.CODE_400, "BaseURL 不能为空");
        }

        try {
            String url = cfg.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
            String body = "{\"model\":\"" + cfg.getModelName() + "\","
                    + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                    + "\"max_tokens\":1}";

            cn.hutool.http.HttpResponse resp = cn.hutool.http.HttpRequest.post(url)
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .timeout(10000)
                    .execute();

            int status = resp.getStatus();
            if (status == 200) {
                return Result.success("连接成功 ✅");
            } else {
                return Result.error(String.valueOf(status), "连接失败，请检查 Base URL 和 API Key 是否正确");
            }
        } catch (Exception e) {
            return Result.error(Constants.CODE_500, "连接异常，请检查网络和配置");
        }
    }

    private AiConfig maskKey(AiConfig cfg) {
        if (cfg == null) return null;
        AiConfig copy = new AiConfig();
        copy.setId(cfg.getId());
        copy.setUserId(cfg.getUserId());
        copy.setProvider(cfg.getProvider());
        copy.setBaseUrl(cfg.getBaseUrl());
        copy.setModelName(cfg.getModelName());
        copy.setChatModelName(cfg.getChatModelName());
        copy.setChatBaseUrl(cfg.getChatBaseUrl());
        copy.setVisionModelName(cfg.getVisionModelName());
        copy.setVisionBaseUrl(cfg.getVisionBaseUrl());
        copy.setTemperature(cfg.getTemperature());
        copy.setEnabled(cfg.getEnabled());
        copy.setUpdateTime(cfg.getUpdateTime());
        copy.setAiActionPolicy(cfg.getAiActionPolicy());
        // 主模型 Key 脱敏
        String key = cfg.getApiKey();
        if (key != null && key.length() > 4) {
            copy.setApiKey(key.substring(0, 4) + "****" + key.substring(key.length() - 2));
        } else {
            copy.setApiKey(key);
        }
        // 对话模型 Key 脱敏（为空时原样返回空串）
        String chatKey = cfg.getChatApiKey();
        if (chatKey != null && chatKey.length() > 4) {
            copy.setChatApiKey(chatKey.substring(0, 4) + "****" + chatKey.substring(chatKey.length() - 2));
        } else {
            copy.setChatApiKey(chatKey);
        }
        // 视觉模型 Key 脱敏
        String visionKey = cfg.getVisionApiKey();
        if (visionKey != null && visionKey.length() > 4) {
            copy.setVisionApiKey(visionKey.substring(0, 4) + "****" + visionKey.substring(visionKey.length() - 2));
        } else {
            copy.setVisionApiKey(visionKey);
        }
        return copy;
    }
}
