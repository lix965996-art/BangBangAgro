package com.farmland.intel.service.impl;

import com.farmland.intel.service.IQwenService;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * 通义千问 AI 服务实现
 */
@Service
@Slf4j
public class QwenServiceImpl implements IQwenService {
    
    @Value("${qwen.api-key:}")
    private String apiKey;
    
    // 通义千问 API 地址
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
    
    // 模型名称（使用 qwen-max 最高级模型）
    private static final String MODEL = "qwen-max";
    
    @Override
    public String askAgriExpert(String userQuestion, double indoorTemp, double indoorHumidity, double outdoorTemp) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "通义千问 API Key 未配置，请检查 QWEN_API_KEY 环境变量。";
            }

            // 1. 构建"人设"和"环境背景" (System Prompt)
            String systemPrompt = "你是一位拥有20年经验的资深农业专家。当前大棚的实时环境数据如下：\n" +
                    "【室内温度】：" + indoorTemp + "°C\n" +
                    "【室内湿度】：" + indoorHumidity + "%\n" +
                    "【室外温度】：" + outdoorTemp + "°C\n" +
                    "请根据上述环境数据回答用户的问题。\n" +
                    "注意：如果温湿度处于危险范围（如湿度>80%或温度>35°C），请在回答开头优先给出红色预警和具体补救措施（如开窗、补水等）。回答风格要专业、简洁。";
            
            // 2. 构建通义千问请求格式
            JSONObject payload = new JSONObject();
            payload.put("model", MODEL);
            
            // 构建input
            JSONObject input = new JSONObject();
            JSONArray messages = new JSONArray();
            messages.add(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.add(new JSONObject().put("role", "user").put("content", userQuestion));
            input.put("messages", messages);
            payload.put("input", input);
            
            // 构建parameters
            JSONObject parameters = new JSONObject();
            parameters.put("temperature", 0.7);
            parameters.put("result_format", "message");
            payload.put("parameters", parameters);

            // 3. 发送 HTTP POST 请求
            String result = HttpRequest.post(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(payload.toString())
                    .timeout(60000)
                    .execute()
                    .body();

            log.info("通义千问响应: {}", result);

            // 4. 解析返回的 JSON 结果
            JSONObject jsonResult = JSONUtil.parseObj(result);

            // 检查是否有错误信息
            if (jsonResult.containsKey("code") && !"200".equals(jsonResult.getStr("code"))) {
                String errorMsg = jsonResult.getStr("message", "未知错误");
                return "通义千问调用失败: " + errorMsg;
            }

            // 提取 AI 回复的核心文本 (通义千问格式)
            if (jsonResult.containsKey("output")) {
                JSONObject output = jsonResult.getJSONObject("output");
                if (output.containsKey("choices")) {
                    JSONArray choices = output.getJSONArray("choices");
                    if (!choices.isEmpty()) {
                        return choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getStr("content");
                    }
                }
                // 兼容旧格式
                if (output.containsKey("text")) {
                    return output.getStr("text");
                }
            }
            
            return "AI 未返回有效响应，请稍后再试。";

        } catch (Exception e) {
            log.error("通义千问调用异常", e);
            return "AI 农艺师正在田间巡逻（连接超时或网络异常），请稍后再试。";
        }
    }
}
