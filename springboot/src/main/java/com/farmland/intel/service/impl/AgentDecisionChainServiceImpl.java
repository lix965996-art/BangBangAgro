package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.AgentDecisionChain;
import com.farmland.intel.mapper.AgentDecisionChainMapper;
import com.farmland.intel.service.IAgentDecisionChainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class AgentDecisionChainServiceImpl extends ServiceImpl<AgentDecisionChainMapper, AgentDecisionChain>
        implements IAgentDecisionChainService {

    @Override
    public void recordStep(String chainId, Integer userId, String triggerSource,
                           String userQuestion, int stepIndex, String stepType,
                           String stepContent, String stepDetail,
                           String modelName, Integer roundNumber, Integer durationMs) {
        try {
            AgentDecisionChain step = new AgentDecisionChain();
            step.setChainId(chainId);
            step.setUserId(userId);
            step.setTriggerSource(triggerSource);
            step.setUserQuestion(userQuestion);
            step.setStepIndex(stepIndex);
            step.setStepType(stepType);
            step.setStepContent(truncate(stepContent, 8000));
            step.setStepDetail(stepDetail);
            step.setModelName(modelName);
            step.setRoundNumber(roundNumber);
            step.setDurationMs(durationMs);
            step.setCreatedAt(new Date());
            save(step);
        } catch (Exception e) {
            log.warn("记录决策链步骤失败: {}", e.getMessage());
        }
    }

    @Override
    public List<AgentDecisionChain> getChainSteps(String chainId) {
        return list(Wrappers.<AgentDecisionChain>lambdaQuery()
                .eq(AgentDecisionChain::getChainId, chainId)
                .orderByAsc(AgentDecisionChain::getStepIndex));
    }

    @Override
    public List<AgentDecisionChain> getRecentChains(int limit) {
        // 获取最近的决策链：取每个 chain_id 的 final_answer 步骤作为摘要
        return list(Wrappers.<AgentDecisionChain>lambdaQuery()
                .eq(AgentDecisionChain::getStepType, "final_answer")
                .orderByDesc(AgentDecisionChain::getCreatedAt)
                .last("LIMIT " + limit));
    }

    @Override
    public void saveSteps(List<AgentDecisionChain> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        try {
            saveBatch(steps);
        } catch (Exception e) {
            log.warn("批量保存决策链步骤失败: {}", e.getMessage());
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
