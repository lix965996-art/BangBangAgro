package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.AgentTaskQueue;
import com.farmland.intel.mapper.AgentTaskQueueMapper;
import com.farmland.intel.service.IAgentTaskQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AgentTaskQueueServiceImpl extends ServiceImpl<AgentTaskQueueMapper, AgentTaskQueue>
        implements IAgentTaskQueueService {

    @Value("${agent.autonomous.task-expiry-hours:24}")
    private int taskExpiryHours;

    @Override
    public AgentTaskQueue createTask(String chainId, String taskType, String priority,
                                     String riskLevel, boolean autoExecute, String farmName,
                                     String actionType, String actionParams, String reasoning,
                                     String knowledgeRefs, BigDecimal confidenceScore) {
        AgentTaskQueue task = new AgentTaskQueue();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setChainId(chainId);
        task.setTaskType(taskType);
        task.setTaskStatus("pending");
        task.setPriority(priority != null ? priority : "medium");
        task.setRiskLevel(riskLevel != null ? riskLevel : "low");
        task.setAutoExecute(autoExecute);
        task.setFarmName(farmName);
        task.setActionType(actionType);
        task.setActionParams(actionParams);
        task.setReasoning(reasoning);
        task.setKnowledgeRefs(knowledgeRefs);
        task.setConfidenceScore(confidenceScore);
        task.setCreatedAt(new Date());
        int expiryHours = Math.max(1, taskExpiryHours);
        task.setExpiresAt(new Date(System.currentTimeMillis() + expiryHours * 3600 * 1000L));
        save(task);
        log.info("创建Agent任务: taskId={}, type={}, risk={}, auto={}", task.getTaskId(), taskType, riskLevel, autoExecute);
        return task;
    }

    @Override
    public List<AgentTaskQueue> getPendingTasks(int limit) {
        return list(Wrappers.<AgentTaskQueue>lambdaQuery()
                .eq(AgentTaskQueue::getTaskStatus, "pending")
                .orderByAsc(AgentTaskQueue::getCreatedAt)
                .last("LIMIT " + limit));
    }

    @Override
    public List<AgentTaskQueue> getPendingApprovalTasks(int limit) {
        return list(Wrappers.<AgentTaskQueue>lambdaQuery()
                .eq(AgentTaskQueue::getTaskStatus, "pending")
                .eq(AgentTaskQueue::getAutoExecute, false)
                .orderByDesc(AgentTaskQueue::getCreatedAt)
                .last("LIMIT " + limit));
    }

    @Override
    public AgentTaskQueue getByTaskId(String taskId) {
        return getOne(Wrappers.<AgentTaskQueue>lambdaQuery().eq(AgentTaskQueue::getTaskId, taskId));
    }

    @Override
    public boolean hasPendingDuplicate(Integer userId, String actionType, String actionParams) {
        long c = count(Wrappers.<AgentTaskQueue>lambdaQuery()
                .eq(AgentTaskQueue::getTaskStatus, "pending")
                .eq(userId != null, AgentTaskQueue::getUserId, userId)
                .eq(AgentTaskQueue::getActionType, actionType)
                .eq(AgentTaskQueue::getActionParams, actionParams));
        return c > 0;
    }

    @Override
    public List<AgentTaskQueue> getPendingApprovalTasksByUser(Integer userId, int limit) {
        return list(Wrappers.<AgentTaskQueue>lambdaQuery()
                .eq(AgentTaskQueue::getTaskStatus, "pending")
                .eq(userId != null, AgentTaskQueue::getUserId, userId)
                .orderByDesc(AgentTaskQueue::getCreatedAt)
                .last("LIMIT " + limit));
    }

    @Override
    public boolean approveTask(String taskId, Integer approvedBy) {
        return update(Wrappers.<AgentTaskQueue>lambdaUpdate()
                .eq(AgentTaskQueue::getTaskId, taskId)
                .eq(AgentTaskQueue::getTaskStatus, "pending")
                .set(AgentTaskQueue::getTaskStatus, "approved")
                .set(AgentTaskQueue::getApprovedBy, approvedBy)
                .set(AgentTaskQueue::getApprovedAt, new Date()));
    }

    @Override
    public boolean rejectTask(String taskId, Integer approvedBy) {
        return update(Wrappers.<AgentTaskQueue>lambdaUpdate()
                .eq(AgentTaskQueue::getTaskId, taskId)
                .eq(AgentTaskQueue::getTaskStatus, "pending")
                .set(AgentTaskQueue::getTaskStatus, "rejected")
                .set(AgentTaskQueue::getApprovedBy, approvedBy)
                .set(AgentTaskQueue::getApprovedAt, new Date()));
    }

    @Override
    public boolean markExecuting(String taskId) {
        return update(Wrappers.<AgentTaskQueue>lambdaUpdate()
                .eq(AgentTaskQueue::getTaskId, taskId)
                .in(AgentTaskQueue::getTaskStatus, "pending", "approved")
                .set(AgentTaskQueue::getTaskStatus, "executing")
                .set(AgentTaskQueue::getExecutedAt, new Date()));
    }

    @Override
    public boolean markCompleted(String taskId, String result) {
        return update(Wrappers.<AgentTaskQueue>lambdaUpdate()
                .eq(AgentTaskQueue::getTaskId, taskId)
                .set(AgentTaskQueue::getTaskStatus, "completed")
                .set(AgentTaskQueue::getExecutionResult, result));
    }

    @Override
    public boolean markFailed(String taskId, String reason) {
        return update(Wrappers.<AgentTaskQueue>lambdaUpdate()
                .eq(AgentTaskQueue::getTaskId, taskId)
                .set(AgentTaskQueue::getTaskStatus, "failed")
                .set(AgentTaskQueue::getExecutionResult, reason));
    }

    @Override
    public List<AgentTaskQueue> getAutoExecutableTasks() {
        return list(Wrappers.<AgentTaskQueue>lambdaQuery()
                .eq(AgentTaskQueue::getTaskStatus, "pending")
                .eq(AgentTaskQueue::getAutoExecute, true)
                .ge(AgentTaskQueue::getExpiresAt, new Date()) // 未过期
                .orderByAsc(AgentTaskQueue::getCreatedAt));
    }

    @Override
    public List<AgentTaskQueue> getByStatus(String status, int limit) {
        return list(Wrappers.<AgentTaskQueue>lambdaQuery()
                .eq(AgentTaskQueue::getTaskStatus, status)
                .orderByDesc(AgentTaskQueue::getCreatedAt)
                .last("LIMIT " + limit));
    }
}
