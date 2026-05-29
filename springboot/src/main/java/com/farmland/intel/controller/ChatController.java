package com.farmland.intel.controller;

// 1. 注意这里：引入的是通义千问【接口】
import com.farmland.intel.common.Result;
import com.farmland.intel.service.IQwenService;
import com.farmland.intel.service.IOneNetService;
import com.farmland.intel.mapper.SensorReadingMapper;
import com.farmland.intel.entity.SensorReading;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.farmland.intel.entity.AiConfig;
import com.farmland.intel.service.ChatModelFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    @PostConstruct
    public void validateApiKey() {
        String provider = qwenProxyProvider == null ? "" : qwenProxyProvider.trim().toLowerCase();
        if ("deepseek".equals(provider)) {
            if (deepseekApiKey == null || deepseekApiKey.trim().isEmpty()) {
                log.warn("qwen.proxy-provider=deepseek 但未配置 DEEPSEEK_API_KEY / deepseek.fallback-api-key，AI 对话功能不可用");
            } else {
                log.info("qwen-proxy 使用 DeepSeek（OpenAI 兼容），前端路径仍为 /api/chat/qwen-proxy");
            }
            if (qwenApiKey == null || qwenApiKey.trim().isEmpty()) {
                log.warn("qwen.api-key 为空：依赖 DashScope 的 /api/chat/ask 将返回未配置提示");
            }
        } else {
            if (qwenApiKey == null || qwenApiKey.trim().isEmpty()) {
                log.warn("DashScope（通义）API Key 未配置，且 qwen.proxy-provider 非 deepseek，AI 对话功能不可用");
            } else {
                log.info("通义千问（DashScope）API Key 已配置");
            }
        }

        // 检查高德地图API密钥
        if ((webKey == null || webKey.trim().isEmpty()) && (jsKey == null || jsKey.trim().isEmpty())) {
            log.warn("AMAP_WEB_KEY and AMAP_JS_KEY environment variables are not set. Map services will be unavailable.");
        } else {
            log.info("High map API keys are configured successfully");
        }
    }

    // 2. 注意这里：注入类型必须是【接口 IQwenService】
    @Autowired
    private IQwenService qwenService;

    @Autowired
    private SensorReadingMapper sensorReadingMapper;

    @Autowired(required = false)
    private IOneNetService oneNetService;

    @Value("${amap.web-key}")
    private String webKey;

    @Value("${amap.js-key}")
    private String jsKey;

    @Value("${amap.js-security-key:}")
    private String jsSecurityKey;

    @Value("${amap.city:430800}")
    private String defaultCity;

    @Value("${qwen.api-key:}")
    private String qwenApiKey;

    /** dashscope | deepseek：deepseek 时仅 /qwen-proxy 走 DeepSeek，不改前端 URL */
    @Value("${qwen.proxy-provider:dashscope}")
    private String qwenProxyProvider;

    @Value("${qwen.api-url:https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation}")
    private String qwenApiUrl;

    @Value("${qwen.model:qwen-max}")
    private String qwenModel;

    @Value("${deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${deepseek.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String deepseekApiUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @PostMapping("/ask")
    public Result chatWithAI(@RequestBody Map<String, String> params) {
        String question = params.get("question");
        Map<String, Object> response = new HashMap<>();

        // 1. 获取室内温湿度 (STM32)
        double indoorTemp = 25.0;
        double indoorHumidity = 60.0;
        
        // 优先尝试从OneNET获取
        boolean gotFromOneNet = false;
        if (oneNetService != null) {
            try {
                Map<String, Object> oneNetData = oneNetService.getDeviceData();
                Object successObj = oneNetData.getOrDefault("success", false);
                if (Boolean.TRUE.equals(successObj)) {
                    Object tempObj = oneNetData.get("temperature");
                    Object humObj = oneNetData.get("humidity");
                    if (tempObj != null && humObj != null) {
                        indoorTemp = Double.parseDouble(tempObj.toString());
                        indoorHumidity = Double.parseDouble(humObj.toString());
                        gotFromOneNet = true;
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
        
        // 如果OneNET失败，从数据库获取最新
        if (!gotFromOneNet) {
            try {
                SensorReading latest = sensorReadingMapper.selectLatest();
                if (latest != null) {
                    indoorTemp = latest.getTemperature();
                    indoorHumidity = latest.getHumidity();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // 2. 获取室外温度 (高德API)
        double outdoorTemp = 20.0; // 默认值
        try {
            String apiKey = (webKey != null && !webKey.isEmpty()) ? webKey : jsKey;
            if (apiKey != null && !apiKey.isEmpty()) {
                // 构建高德API URL，包含安全密钥参数
                String url = buildWeatherUrl(apiKey);
                String res = HttpUtil.get(url);
                JSONObject json = JSONUtil.parseObj(res);
                if ("1".equals(json.getStr("status"))) {
                    JSONArray lives = json.getJSONArray("lives");
                    if (lives != null && !lives.isEmpty()) {
                        outdoorTemp = Double.parseDouble(lives.getJSONObject(0).getStr("temperature"));
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // 3. 调用接口定义的方法
        String answer = qwenService.askAgriExpert(question, indoorTemp, indoorHumidity, outdoorTemp);

        response.put("answer", answer);
        return Result.success(response);
    }

    private String buildWeatherUrl(String apiKey) {
        StringBuilder url = new StringBuilder("https://restapi.amap.com/v3/weather/weatherInfo")
                .append("?city=").append(defaultCity)
                .append("&key=").append(apiKey)
                .append("&extensions=base")
                .append("&sdk=server");
        if ((webKey == null || webKey.isEmpty()) && jsSecurityKey != null && !jsSecurityKey.isEmpty()) {
            url.append("&jscode=").append(jsSecurityKey);
        }
        return url.toString();
    }

    /**
     * 通义千问API代理接口（解决前端CORS问题）
     * 用于前端需要直接调用通义千问进行分析的场景
     * @param params 包含 prompt 和可选的 systemPrompt
     * @return 通义千问的响应结果
     */
    @Autowired
    private ChatModelFactory chatModelFactory;

    @PostMapping("/qwen-proxy")
    public Map<String, Object> qwenProxy(@RequestBody Map<String, String> params) {
        String prompt = params.get("prompt");
        String systemPrompt = params.getOrDefault("systemPrompt", "你是一个农业专家，善于分析农田数据并给出产量和市场预测。请以JSON格式返回结果。");
        Map<String, Object> response = new HashMap<>();

        if (prompt == null || prompt.trim().isEmpty()) {
            response.put("code", 400);
            response.put("message", "prompt不能为空");
            return response;
        }

        try {
            String provider = qwenProxyProvider == null ? "" : qwenProxyProvider.trim().toLowerCase();

            // 根据 provider 选择配置
            AiConfig aiConfig = new AiConfig();
            if ("deepseek".equals(provider)) {
                aiConfig.setProvider("deepseek");
                aiConfig.setBaseUrl(deepseekApiUrl.replaceAll("/chat/completions$", "").replaceAll("/+$", ""));
                aiConfig.setApiKey(deepseekApiKey);
                aiConfig.setModelName(deepseekModel);
            } else {
                aiConfig.setProvider("qwen");
                aiConfig.setBaseUrl(qwenApiUrl.replaceAll("/api/v1/services/aigc/text-generation/generation$", "").replaceAll("/+$", ""));
                aiConfig.setApiKey(qwenApiKey);
                aiConfig.setModelName(qwenModel);
            }
            aiConfig.setTemperature(new BigDecimal("0.7"));

            ChatClient client = chatModelFactory.getChatClient(aiConfig);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.add(new UserMessage(prompt));

            String content = client.prompt()
                    .messages(messages)
                    .call()
                    .content();

            log.info("qwen-proxy({}) 响应长度: {}", provider, content != null ? content.length() : 0);
            return buildProxySuccessResponse(response, content);
        } catch (Exception e) {
            log.error("qwen-proxy 调用异常", e);
            response.put("code", 500);
            response.put("message", "AI 服务暂时不可用，请稍后重试");
            response.put("data", null);
        }

        return response;
    }

    /** 将模型返回的正文写入与原先一致的 code/message/data 结构 */
    private Map<String, Object> buildProxySuccessResponse(Map<String, Object> response, String content) {
        if (content != null) {
            content = content.trim();
            if (content.startsWith("```json")) {
                content = content.substring(7);
            } else if (content.startsWith("```")) {
                content = content.substring(3);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            content = content.trim();

            try {
                JSONObject jsonContent = JSONUtil.parseObj(content);
                response.put("code", 200);
                response.put("message", "success");
                response.put("data", jsonContent);
            } catch (Exception e) {
                response.put("code", 200);
                response.put("message", "success");
                response.put("data", content);
            }
        } else {
            response.put("code", 500);
            response.put("message", "AI 未返回有效响应");
            response.put("data", null);
        }
        return response;
    }
}

