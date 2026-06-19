package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.AgentTaskQueue;

import java.util.List;

/**
 * Agent 任务队列服务
 */
public interface IAgentTaskQueueService extends IService<AgentTaskQueue> {

    /**
     * 创建任务
     */
    AgentTaskQueue createTask(String chainId, String taskType, String priority,
                              String riskLevel, boolean autoExecute, String farmName,
                              String actionType, String actionParams, String reasoning,
                              String knowledgeRefs, java.math.BigDecimal confidenceScore);

    /**
     * 获取待处理任务列表
     */
    List<AgentTaskQueue> getPendingTasks(int limit);

    /**
     * 获取待审批任务（高风险、非自动执行）
     */
    List<AgentTaskQueue> getPendingApprovalTasks(int limit);

    /**
     * 按 taskId 取单条任务
     */
    AgentTaskQueue getByTaskId(String taskId);

    /**
     * 同用户是否已存在相同的 pending 任务（去重，防 LLM 重复入队）
     */
    boolean hasPendingDuplicate(Integer userId, String actionType, String actionParams);

    /**
     * 当前用户的待审批任务（按用户隔离）
     */
    List<AgentTaskQueue> getPendingApprovalTasksByUser(Integer userId, int limit);

    /**
     * 审批任务
     */
    boolean approveTask(String taskId, Integer approvedBy);

    /**
     * 拒绝任务
     */
    boolean rejectTask(String taskId, Integer approvedBy);

    /**
     * 标记任务执行中
     */
    boolean markExecuting(String taskId);

    /**
     * 标记任务完成
     */
    boolean markCompleted(String taskId, String result);

    /**
     * 标记任务失败
     */
    boolean markFailed(String taskId, String reason);

    /**
     * 获取可自动执行的任务
     */
    List<AgentTaskQueue> getAutoExecutableTasks();

    /**
     * 按状态获取任务
     */
    List<AgentTaskQueue> getByStatus(String status, int limit);
}
