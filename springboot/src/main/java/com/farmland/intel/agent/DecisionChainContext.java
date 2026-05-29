package com.farmland.intel.agent;

import com.farmland.intel.entity.AgentDecisionChain;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 决策链上下文：在一次完整的 Function Calling 推理过程中记录每一步。
 * 使用 ThreadLocal 传递，避免修改 AgentService 方法签名。
 */
@Data
public class DecisionChainContext {

    /** 本次推理的唯一标识 */
    private String chainId;

    /** 触发用户, null=系统自主 */
    private Integer userId;

    /** 触发来源: user_chat / auto_patrol / sensor_event / scheduled */
    private String triggerSource;

    /** 用户原始问题或触发描述 */
    private String userQuestion;

    /** 当前步骤序号 */
    private int currentStep = 0;

    /** 当前步骤开始时间 */
    private long stepStartTime;

    /** 收集的所有步骤（最后批量写入 DB） */
    private List<AgentDecisionChain> steps = new ArrayList<>();

    /** 模型名称 */
    private String modelName;

    private static final ThreadLocal<DecisionChainContext> THREAD_LOCAL = new ThreadLocal<>();

    public static DecisionChainContext create(Integer userId, String triggerSource, String question) {
        DecisionChainContext ctx = new DecisionChainContext();
        ctx.setChainId(UUID.randomUUID().toString().replace("-", ""));
        ctx.setUserId(userId);
        ctx.setTriggerSource(triggerSource != null ? triggerSource : "user_chat");
        ctx.setUserQuestion(question);
        ctx.setStepStartTime(System.currentTimeMillis());
        THREAD_LOCAL.set(ctx);
        return ctx;
    }

    public static DecisionChainContext get() {
        return THREAD_LOCAL.get();
    }

    public static void clear() {
        THREAD_LOCAL.remove();
    }

    /** 记录一个步骤 */
    public void recordStep(String stepType, String stepContent, String stepDetail,
                           Integer roundNumber) {
        AgentDecisionChain step = new AgentDecisionChain();
        step.setChainId(chainId);
        step.setUserId(userId);
        step.setTriggerSource(triggerSource);
        step.setUserQuestion(userQuestion);
        step.setStepIndex(currentStep++);
        step.setStepType(stepType);
        step.setStepContent(truncate(stepContent, 8000));
        step.setStepDetail(stepDetail);
        step.setModelName(modelName);
        step.setRoundNumber(roundNumber);
        step.setDurationMs((int) (System.currentTimeMillis() - stepStartTime));
        steps.add(step);
        stepStartTime = System.currentTimeMillis();
    }

    /** 标记新一轮步骤开始 */
    public void startStep() {
        stepStartTime = System.currentTimeMillis();
    }

    public void startRound() {
        stepStartTime = System.currentTimeMillis();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
