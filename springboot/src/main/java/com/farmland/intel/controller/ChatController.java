package com.farmland.intel.controller;

// 1. 注意这里：引入的是通义千问【接口】
import com.farmland.intel.service.IQwenService;
import com.farmland.intel.service.IOneNetService;
import com.farmland.intel.mapper.SensorReadingMapper;
import com.farmland.intel.entity.SensorReading;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.PostConstruct;

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
                log.error("qwen.proxy-provider=deepseek 但未配置 DEEPSEEK_API_KEY / deepseek.fallback-api-key");
                throw new IllegalStateException("使用 DeepSeek 时请在 application-local.yml 配置 deepseek.fallback-api-key 或环境变量 DEEPSEEK_API_KEY");
            }
            log.info("qwen-proxy 使用 DeepSeek（OpenAI 兼容），前端路径仍为 /api/chat/qwen-proxy");
            if (qwenApiKey == null || qwenApiKey.trim().isEmpty()) {
                log.warn("qwen.api-key 为空：依赖 DashScope 的 /api/chat/ask 将返回未配置提示");
            }
        } else {
            if (qwenApiKey == null || qwenApiKey.trim().isEmpty()) {
                log.error("DashScope（通义）API Key 未配置，且 qwen.proxy-provider 非 deepseek");
                throw new IllegalStateException("请配置 QWEN_API_KEY / qwen.fallback-api-key，或设置 qwen.proxy-provider=deepseek 并配置 DeepSeek Key");
            }
            log.info("通义千问（DashScope）API Key 已配置");
        }

        // 检查高德地图API密钥
        if ((webKey == null || webKey.trim().isEmpty()) && (jsKey == null || jsKey.trim().isEmpty())) {
            log.error("AMAP_WEB_KEY and AMAP_JS_KEY environment variables are not set. At least one must be configured for map services.");
            throw new IllegalStateException("At least one of AMAP_WEB_KEY or AMAP_JS_KEY must be set in environment variables");
        }
        log.info("High map API keys are configured successfully");
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
    public Map<String, Object> chatWithAI(@RequestBody Map<String, String> params) {
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
                if ((Boolean) oneNetData.getOrDefault("success", false)) {
                    indoorTemp = Double.parseDouble(oneNetData.get("temperature").toString());
                    indoorHumidity = Double.parseDouble(oneNetData.get("humidity").toString());
                    gotFromOneNet = true;
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

        response.put("code", 200);
        response.put("answer", answer);
        return response;
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
            String result;
            JSONObject jsonResult;

            if ("deepseek".equals(provider)) {
                JSONObject payload = new JSONObject();
                payload.set("model", deepseekModel);
                JSONArray messages = new JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.set("role", "system");
                systemMsg.set("content", systemPrompt);
                messages.add(systemMsg);
                JSONObject userMsg = new JSONObject();
                userMsg.set("role", "user");
                userMsg.set("content", prompt);
                messages.add(userMsg);
                payload.set("messages", messages);
                payload.set("temperature", 0.7);

                result = HttpRequest.post(deepseekApiUrl)
                        .header("Authorization", "Bearer " + deepseekApiKey.trim())
                        .header("Content-Type", "application/json")
                        .body(payload.toString())
                        .timeout(120000)
                        .execute()
                        .body();

                log.info("qwen-proxy(DeepSeek) 响应: {}", result);
                jsonResult = JSONUtil.parseObj(result);

                if (jsonResult.containsKey("error")) {
                    JSONObject err = jsonResult.getJSONObject("error");
                    String errorMsg = err != null ? err.getStr("message", "未知错误") : "未知错误";
                    response.put("code", 500);
                    response.put("message", "调用失败: " + errorMsg);
                    response.put("data", null);
                    return response;
                }

                String content = null;
                if (jsonResult.containsKey("choices")) {
                    JSONArray choices = jsonResult.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        JSONObject choice0 = choices.getJSONObject(0);
                        if (choice0 != null && choice0.containsKey("message")) {
                            content = choice0.getJSONObject("message").getStr("content");
                        }
                    }
                }
                return buildProxySuccessResponse(response, content);
            }

            // DashScope（通义）请求格式
            JSONObject payload = new JSONObject();
            payload.set("model", qwenModel);

            JSONObject input = new JSONObject();
            JSONArray messages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.set("role", "system");
            systemMsg.set("content", systemPrompt);
            messages.add(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.set("role", "user");
            userMsg.set("content", prompt);
            messages.add(userMsg);

            input.set("messages", messages);
            payload.set("input", input);

            JSONObject parameters = new JSONObject();
            parameters.set("temperature", 0.7);
            parameters.set("result_format", "message");
            payload.set("parameters", parameters);

            result = HttpRequest.post(qwenApiUrl)
                    .header("Authorization", "Bearer " + qwenApiKey)
                    .header("Content-Type", "application/json")
                    .body(payload.toString())
                    .timeout(60000)
                    .execute()
                    .body();

            log.info("通义千问代理响应: {}", result);

            jsonResult = JSONUtil.parseObj(result);

            if (jsonResult.containsKey("code") && !"200".equals(jsonResult.getStr("code"))) {
                String errorMsg = jsonResult.getStr("message", "未知错误");
                response.put("code", 500);
                response.put("message", "通义千问调用失败: " + errorMsg);
                response.put("data", null);
                return response;
            }

            String content = null;
            if (jsonResult.containsKey("output")) {
                JSONObject output = jsonResult.getJSONObject("output");
                if (output.containsKey("choices")) {
                    JSONArray choices = output.getJSONArray("choices");
                    if (!choices.isEmpty()) {
                        content = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getStr("content");
                    }
                }
                if (content == null && output.containsKey("text")) {
                    content = output.getStr("text");
                }
            }

            return buildProxySuccessResponse(response, content);
        } catch (Exception e) {
            log.error("qwen-proxy 调用异常", e);
            response.put("code", 500);
            response.put("message", "AI 服务调用异常: " + e.getMessage());
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

