package com.farmland.intel.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent 风险评估器
 *
 * 根据任务类型、操作动作、传感器严重程度等维度评估风险等级和置信度，
 * 决定任务是否可以自动执行。
 */
@Component
@Slf4j
public class ConfidenceEvaluator {

    @Value("${agent.autonomous.confidence-threshold:70}")
    private int confidenceThreshold;

    // 操作风险映射
    private static final Map<String, String> ACTION_RISK_MAP = new HashMap<>();
    static {
        // 低风险：开关类操作
        ACTION_RISK_MAP.put("irrigation_on", "low");
        ACTION_RISK_MAP.put("irrigation_off", "low");
        ACTION_RISK_MAP.put("led_on", "low");
        ACTION_RISK_MAP.put("led_off", "low");
        ACTION_RISK_MAP.put("fan_on", "low");
        ACTION_RISK_MAP.put("fan_off", "low");
        // 中风险：通知类操作
        ACTION_RISK_MAP.put("send_notification", "medium");
        ACTION_RISK_MAP.put("generate_report", "medium");
        // 高风险：采购、删除等不可逆操作
        ACTION_RISK_MAP.put("create_purchase", "high");
        ACTION_RISK_MAP.put("delete_record", "high");
    }

    // 严重程度权重
    private static final Map<String, Integer> SEVERITY_WEIGHT = new HashMap<>();
    static {
        SEVERITY_WEIGHT.put("low", 10);
        SEVERITY_WEIGHT.put("medium", 25);
        SEVERITY_WEIGHT.put("high", 40);
        SEVERITY_WEIGHT.put("critical", 60);
    }

    /**
     * 评估结果
     */
    public static class EvaluationResult {
        private String riskLevel;
        private BigDecimal confidenceScore;
        private boolean autoExecute;
        private String reasoning;

        public String getRiskLevel() { return riskLevel; }
        public BigDecimal getConfidenceScore() { return confidenceScore; }
        public boolean isAutoExecute() { return autoExecute; }
        public String getReasoning() { return reasoning; }

        @Override
        public String toString() {
            return String.format("risk=%s, confidence=%s, auto=%s", riskLevel, confidenceScore, autoExecute);
        }
    }

    /**
     * 评估任务风险
     *
     * @param actionType    操作动作类型
     * @param severity      传感器事件严重程度（可为null）
     * @param metricName    指标名称（可为null）
     * @param currentValue  当前值（可为null）
     * @param thresholdValue 阈值（可为null）
     * @return 评估结果
     */
    public EvaluationResult evaluate(String actionType, String severity,
                                     String metricName, BigDecimal currentValue,
                                     BigDecimal thresholdValue) {
        EvaluationResult result = new EvaluationResult();

        // 1. 确定风险等级
        String riskLevel = ACTION_RISK_MAP.getOrDefault(actionType, "medium");
        // 如果传感器事件是 critical，升级风险
        if ("critical".equals(severity) && "low".equals(riskLevel)) {
            riskLevel = "medium";
        }
        result.riskLevel = riskLevel;

        // 2. 计算置信度（0-100）
        int score = 50; // 基础分

        // 传感器严重程度加分（越严重越需要处理）
        if (severity != null) {
            score += SEVERITY_WEIGHT.getOrDefault(severity, 0);
        }

        // 如果有明确的阈值突破，加分
        if (currentValue != null && thresholdValue != null) {
            double diff = Math.abs(currentValue.doubleValue() - thresholdValue.doubleValue());
            double ratio = diff / thresholdValue.doubleValue();
            if (ratio > 0.3) score += 15;
            else if (ratio > 0.15) score += 10;
            else if (ratio > 0.05) score += 5;
        }

        // 高风险操作扣分
        if ("high".equals(riskLevel)) {
            score -= 30;
        } else if ("medium".equals(riskLevel)) {
            score -= 10;
        }

        // 钳制到 0-100
        score = Math.max(0, Math.min(100, score));
        result.confidenceScore = BigDecimal.valueOf(score);

        // 3. 决定是否自动执行
        boolean autoExec = "low".equals(riskLevel) && score >= confidenceThreshold;
        result.autoExecute = autoExec;

        // 4. 生成推理说明
        StringBuilder reasoning = new StringBuilder();
        reasoning.append(String.format("操作[%s]风险等级=%s", actionType, riskLevel));
        if (severity != null) {
            reasoning.append(String.format(", 传感器严重程度=%s", severity));
        }
        reasoning.append(String.format(", 置信度=%d/100", score));
        reasoning.append(String.format(", 结论=%s", autoExec ? "自动执行" : "需人工审批"));
        result.reasoning = reasoning.toString();

        log.debug("风险评估: {}", result);
        return result;
    }

    /**
     * 简化评估（仅基于动作类型）
     */
    public EvaluationResult evaluate(String actionType) {
        return evaluate(actionType, null, null, null, null);
    }
}
