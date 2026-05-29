package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.AgentDecisionChain;

import java.util.List;

/**
 * Agent 决策链服务
 */
public interface IAgentDecisionChainService extends IService<AgentDecisionChain> {

    /**
     * 记录一个决策步骤
     */
    void recordStep(String chainId, Integer userId, String triggerSource,
                    String userQuestion, int stepIndex, String stepType,
                    String stepContent, String stepDetail,
                    String modelName, Integer roundNumber, Integer durationMs);

    /**
     * 获取某条决策链的全部步骤
     */
    List<AgentDecisionChain> getChainSteps(String chainId);

    /**
     * 获取最近的决策链列表（按 chain_id 分组，取最新一条）
     */
    List<AgentDecisionChain> getRecentChains(int limit);

    /**
     * 批量保存决策步骤
     */
    void saveSteps(List<AgentDecisionChain> steps);
}
