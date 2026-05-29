package com.farmland.intel.service;

import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模型调用日志记录器
 * 专门记录 AI 模型调用的详细信息，包括：
 * - 调用模型名称
 * - 调用次数
 * - Token 使用量
 * - 费用估算
 */
@Component
@Slf4j
public class ModelCallLogger {
    
    // 日志文件路径（使用系统临时目录，避免相对路径不可预测）
    private static final String LOG_FILE = System.getProperty("java.io.tmpdir") + File.separator + "model_call_log.txt";
    
    // 统计计数器
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);
    
    // 通义千问定价（元/千tokens）- qwen-max
    // 输入：0.02元/千tokens，输出：0.06元/千tokens
    private static final BigDecimal INPUT_PRICE_PER_1K = new BigDecimal("0.02");
    private static final BigDecimal OUTPUT_PRICE_PER_1K = new BigDecimal("0.06");
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 记录模型调用
     * @param model 模型名称
     * @param userQuestion 用户问题
     * @param response API 响应
     * @param success 是否成功
     * @param toolCalls 工具调用次数
     */
    public void logModelCall(String model, String userQuestion, JSONObject response, boolean success, int toolCalls) {
        int callNumber = totalCalls.incrementAndGet();
        
        // 解析 token 使用量
        long inputTokens = 0;
        long outputTokens = 0;
        String finishReason = "unknown";
        
        if (response != null && response.containsKey("usage")) {
            JSONObject usage = response.getJSONObject("usage");
            if (usage != null) {
                inputTokens = usage.getLong("input_tokens", 0L);
                outputTokens = usage.getLong("output_tokens", 0L);
            }
        }
        
        if (response != null && response.containsKey("output")) {
            JSONObject output = response.getJSONObject("output");
            if (output != null && output.containsKey("choices")) {
                try {
                    finishReason = output.getJSONArray("choices")
                        .getJSONObject(0)
                        .getStr("finish_reason", "unknown");
                } catch (Exception ignored) {}
            }
        }
        
        // 累计 token
        totalInputTokens.addAndGet(inputTokens);
        totalOutputTokens.addAndGet(outputTokens);
        
        // 计算费用
        BigDecimal inputCost = calculateCost(inputTokens, INPUT_PRICE_PER_1K);
        BigDecimal outputCost = calculateCost(outputTokens, OUTPUT_PRICE_PER_1K);
        BigDecimal totalCost = inputCost.add(outputCost);
        
        // 构建日志内容
        String line80 = repeat("=", 80);
        String line40 = repeat("-", 40);
        
        StringBuilder logContent = new StringBuilder();
        logContent.append("\n").append(line80).append("\n");
        logContent.append("【模型调用日志 #").append(callNumber).append("】\n");
        logContent.append(line80).append("\n");
        logContent.append("时间: ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        logContent.append("模型: ").append(model).append("\n");
        logContent.append("状态: ").append(success ? "✓ 成功" : "✗ 失败").append("\n");
        logContent.append("完成原因: ").append(finishReason).append("\n");
        logContent.append(line40).append("\n");
        logContent.append("用户问题: ").append(truncate(userQuestion, 100)).append("\n");
        logContent.append(line40).append("\n");
        logContent.append("Token 使用量:\n");
        logContent.append("  · 输入 tokens: ").append(inputTokens).append("\n");
        logContent.append("  · 输出 tokens: ").append(outputTokens).append("\n");
        logContent.append("  · 总计 tokens: ").append(inputTokens + outputTokens).append("\n");
        logContent.append(line40).append("\n");
        logContent.append("本次费用估算:\n");
        logContent.append("  · 输入费用: ¥").append(inputCost.setScale(6, RoundingMode.HALF_UP)).append("\n");
        logContent.append("  · 输出费用: ¥").append(outputCost.setScale(6, RoundingMode.HALF_UP)).append("\n");
        logContent.append("  · 本次总计: ¥").append(totalCost.setScale(6, RoundingMode.HALF_UP)).append("\n");
        logContent.append(line40).append("\n");
        logContent.append("工具调用次数: ").append(toolCalls).append("\n");
        logContent.append(line40).append("\n");
        logContent.append("累计统计:\n");
        logContent.append("  · 总调用次数: ").append(callNumber).append("\n");
        logContent.append("  · 累计输入 tokens: ").append(totalInputTokens.get()).append("\n");
        logContent.append("  · 累计输出 tokens: ").append(totalOutputTokens.get()).append("\n");
        logContent.append("  · 累计总费用: ¥").append(calculateTotalCost().setScale(4, RoundingMode.HALF_UP)).append("\n");
        logContent.append(line80).append("\n");
        
        log.debug("Model call logged: call={}, model={}, toolCalls={}", callNumber, model, toolCalls);
        
        // 写入文件
        writeToFile(logContent.toString());
    }
    
    /**
     * 记录 API 错误
     */
    public void logApiError(String model, String userQuestion, String errorCode, String errorMessage) {
        int callNumber = totalCalls.incrementAndGet();
        
        String line80 = repeat("=", 80);
        String line40 = repeat("-", 40);
        
        StringBuilder logContent = new StringBuilder();
        logContent.append("\n").append(line80).append("\n");
        logContent.append("【模型调用错误 #").append(callNumber).append("】\n");
        logContent.append(line80).append("\n");
        logContent.append("时间: ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        logContent.append("模型: ").append(model).append("\n");
        logContent.append("状态: ✗ 错误\n");
        logContent.append(line40).append("\n");
        logContent.append("用户问题: ").append(truncate(userQuestion, 100)).append("\n");
        logContent.append(line40).append("\n");
        logContent.append("错误代码: ").append(errorCode).append("\n");
        logContent.append("错误信息: ").append(errorMessage).append("\n");
        logContent.append(line80).append("\n");
        
        log.error("Model API error logged: call={}, model={}, code={}, message={}", callNumber, model, errorCode, errorMessage);
        writeToFile(logContent.toString());
    }
    
    /**
     * 获取统计摘要
     */
    public String getStatsSummary() {
        String line80 = repeat("*", 80);
        
        StringBuilder summary = new StringBuilder();
        summary.append("\n").append(line80).append("\n");
        summary.append("【模型调用统计摘要】\n");
        summary.append(line80).append("\n");
        summary.append("总调用次数: ").append(totalCalls.get()).append("\n");
        summary.append("累计输入 tokens: ").append(totalInputTokens.get()).append("\n");
        summary.append("累计输出 tokens: ").append(totalOutputTokens.get()).append("\n");
        summary.append("累计总 tokens: ").append(totalInputTokens.get() + totalOutputTokens.get()).append("\n");
        summary.append("累计总费用: ¥").append(calculateTotalCost().setScale(4, RoundingMode.HALF_UP)).append("\n");
        summary.append(line80).append("\n");
        return summary.toString();
    }
    
    /**
     * 计算费用
     */
    private BigDecimal calculateCost(long tokens, BigDecimal pricePerThousand) {
        return new BigDecimal(tokens)
            .divide(new BigDecimal(1000), 10, RoundingMode.HALF_UP)
            .multiply(pricePerThousand);
    }
    
    /**
     * 计算累计总费用
     */
    private BigDecimal calculateTotalCost() {
        BigDecimal inputCost = calculateCost(totalInputTokens.get(), INPUT_PRICE_PER_1K);
        BigDecimal outputCost = calculateCost(totalOutputTokens.get(), OUTPUT_PRICE_PER_1K);
        return inputCost.add(outputCost);
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }
    
    /**
     * 重复字符（兼容 Java 8）
     */
    private String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
    
    /**
     * 写入日志文件
     */
    private synchronized void writeToFile(String content) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            writer.print(content);
        } catch (Exception e) {
            log.warn("写入模型调用日志文件失败: {}", e.getMessage());
        }
    }
}
