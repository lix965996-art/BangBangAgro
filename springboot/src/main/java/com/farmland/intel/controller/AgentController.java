package com.farmland.intel.controller;

import com.farmland.intel.agent.AgentAction;
import com.farmland.intel.agent.AgentActionResult;
import com.farmland.intel.agent.AgentPlan;
import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.AgentDecisionChain;
import com.farmland.intel.entity.AgentTaskQueue;
import com.farmland.intel.entity.AgentUserMemory;
import com.farmland.intel.entity.SensorEvent;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.AgentService;
import com.farmland.intel.service.IAgentDecisionChainService;
import com.farmland.intel.service.IAgentTaskQueueService;
import com.farmland.intel.service.IAgentUserMemoryService;
import com.farmland.intel.service.ISensorEventService;
import com.farmland.intel.utils.TokenUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent 编排接口：
 * 1) /api/agent/plan 生成计划（仅建议 + 动作列表），需要前端确认；可选 body.history 传近期多轮对话。
 * 2) /api/agent/execute 执行已确认的动作（受白名单限制）。
 * 3) /api/agent/memory* 用户级长期记忆（偏好 + 对话滚动摘要）；POST /memory/clear 按 scope 清空。
 */
@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {

    @Autowired
    private AgentService agentService;

    @Autowired(required = false)
    private IAgentUserMemoryService agentUserMemoryService;

    @Autowired(required = false)
    private IAgentDecisionChainService decisionChainService;

    @Autowired(required = false)
    private IAgentTaskQueueService taskQueueService;

    @Autowired(required = false)
    private ISensorEventService sensorEventService;

    @PostMapping("/plan")
    @SuppressWarnings("unchecked")
    public Result plan(@RequestBody Map<String, Object> payload) {
        String question = payload == null ? null : (String) payload.get("question");
        if (question == null || question.trim().isEmpty()) {
            return Result.error(Constants.CODE_400, "question 不能为空");
        }

        User user = TokenUtils.getCurrentUser();
        Integer userId = user != null ? user.getId() : null;

        List<Map<String, Object>> history = null;
        if (payload != null && payload.get("history") instanceof List) {
            history = (List<Map<String, Object>>) payload.get("history");
            // L6: 限制客户端传入的历史轮数，缩小伪造 assistant 轮次的提示注入面(只保留最近20轮)
            if (history != null && history.size() > 20) {
                history = history.subList(history.size() - 20, history.size());
            }
        }

        // mode: "agent"（默认，主推理模型+工具）| "chat"（对话模型，轻量快速）
        String mode = payload != null && payload.get("mode") instanceof String
                ? (String) payload.get("mode") : "agent";

        AgentPlan plan;
        try {
            plan = agentService.buildPlan(userId, question, history, mode);
        } catch (Exception e) {
            log.error("/api/agent/plan 生成计划异常", e);
            return Result.error(Constants.CODE_500, "生成计划失败，请稍后重试");
        }
        if (plan == null) {
            return Result.error(Constants.CODE_500, "生成计划失败：空结果");
        }
        // 确保每个动作有唯一 id（actions 不应为 null，防御性处理避免 NPE→500）
        List<AgentAction> actions = plan.getActions();
        if (actions == null) {
            plan.setActions(new ArrayList<>());
            actions = plan.getActions();
        }
        actions.forEach(a -> {
            if (a != null && (a.getId() == null || a.getId().trim().isEmpty())) {
                a.setId("act-" + UUID.randomUUID());
            }
        });
        return Result.success(plan);
    }

    @PostMapping("/execute")
    public Result execute(@RequestBody ExecuteRequest request) {
        if (request == null || request.getActions() == null || request.getActions().isEmpty()) {
            return Result.error(Constants.CODE_400, "actions 不能为空");
        }

        // 只保留白名单类型
        List<AgentAction> actions = request.getActions().stream()
                .filter(a -> a != null && a.getType() != null)
                .collect(Collectors.toList());

        List<AgentActionResult> results = agentService.executeActions(actions);
        return Result.success(results);
    }

    /**
     * 读取当前登录用户的 Agent 记忆（偏好 + 对话摘要）。
     */
    @GetMapping("/memory")
    public Result getMemory() {
        User user = TokenUtils.getCurrentUser();
        if (user == null) {
            return Result.error(Constants.CODE_401, "未登录");
        }
        if (agentUserMemoryService == null) {
            return Result.error(Constants.CODE_500, "记忆服务未配置");
        }
        AgentUserMemory row = agentUserMemoryService.getOrCreate(user.getId());
        return Result.success(row);
    }

    /**
     * 追加一条用户显式偏好（写入 preferences，供后续 plan 注入 system prompt）。
     */
    @PostMapping("/memory/note")
    public Result appendMemoryNote(@RequestBody Map<String, Object> body) {
        User user = TokenUtils.getCurrentUser();
        if (user == null) {
            return Result.error(Constants.CODE_401, "未登录");
        }
        if (agentUserMemoryService == null) {
            return Result.error(Constants.CODE_500, "记忆服务未配置");
        }
        String note = body == null ? null : (String) body.get("note");
        if (!StringUtils.hasText(note)) {
            return Result.error(Constants.CODE_400, "note 不能为空");
        }
        agentUserMemoryService.appendPreferenceNote(user.getId(), note.trim());
        return Result.success(agentUserMemoryService.getOrCreate(user.getId()));
    }

    /**
     * 按范围清空持久记忆：{@code all} | {@code preferences} | {@code summary}。
     */
    @PostMapping("/memory/clear")
    public Result clearMemory(@RequestBody(required = false) Map<String, Object> body) {
        User user = TokenUtils.getCurrentUser();
        if (user == null) {
            return Result.error(Constants.CODE_401, "未登录");
        }
        if (agentUserMemoryService == null) {
            return Result.error(Constants.CODE_500, "记忆服务未配置");
        }
        String scope = body == null ? null : (String) body.get("scope");
        if (!StringUtils.hasText(scope)) {
            scope = "all";
        }
        agentUserMemoryService.clearMemory(user.getId(), scope.trim());
        return Result.success(agentUserMemoryService.getOrCreate(user.getId()));
    }

    /**
     * 获取最近的决策链列表
     */
    @GetMapping("/decision-chains")
    public Result getDecisionChains(@RequestParam(defaultValue = "20") int limit) {
        if (decisionChainService == null) {
            return Result.error(Constants.CODE_500, "决策链服务未配置");
        }
        List<AgentDecisionChain> chains = decisionChainService.getRecentChains(limit);
        return Result.success(chains);
    }

    /**
     * 获取某条决策链的完整步骤
     */
    @GetMapping("/decision-chain/{chainId}")
    public Result getDecisionChainDetail(@PathVariable String chainId) {
        if (decisionChainService == null) {
            return Result.error(Constants.CODE_500, "决策链服务未配置");
        }
        if (!StringUtils.hasText(chainId)) {
            return Result.error(Constants.CODE_400, "chainId 不能为空");
        }
        List<AgentDecisionChain> steps = decisionChainService.getChainSteps(chainId);
        return Result.success(steps);
    }

    // ══════════════════════════════════════════════════
    // 任务队列管理接口
    // ══════════════════════════════════════════════════

    /**
     * 获取任务列表（可按状态过滤）
     */
    @GetMapping("/tasks")
    public Result getTasks(@RequestParam(required = false) String status,
                           @RequestParam(defaultValue = "20") int limit) {
        if (taskQueueService == null) {
            return Result.error(Constants.CODE_500, "任务队列服务未配置");
        }
        List<AgentTaskQueue> tasks;
        if (StringUtils.hasText(status)) {
            tasks = taskQueueService.getByStatus(status, limit);
        } else {
            tasks = taskQueueService.getPendingTasks(limit);
        }
        return Result.success(tasks);
    }

    /**
     * 获取待审批任务（高风险、非自动执行）
     */
    @GetMapping("/tasks/pending-approval")
    public Result getPendingApprovalTasks(@RequestParam(defaultValue = "20") int limit) {
        if (taskQueueService == null) {
            return Result.error(Constants.CODE_500, "任务队列服务未配置");
        }
        return Result.success(taskQueueService.getPendingApprovalTasks(limit));
    }

    /**
     * 审批通过任务
     */
    @PostMapping("/task/{taskId}/approve")
    public Result approveTask(@PathVariable String taskId) {
        if (taskQueueService == null) {
            return Result.error(Constants.CODE_500, "任务队列服务未配置");
        }
        User user = TokenUtils.getCurrentUser();
        Integer userId = user != null ? user.getId() : null;
        boolean ok = taskQueueService.approveTask(taskId, userId);
        return ok ? Result.success("已批准") : Result.error(Constants.CODE_500, "审批失败，任务可能不存在或状态已变更");
    }

    /**
     * 拒绝任务
     */
    @PostMapping("/task/{taskId}/reject")
    public Result rejectTask(@PathVariable String taskId) {
        if (taskQueueService == null) {
            return Result.error(Constants.CODE_500, "任务队列服务未配置");
        }
        User user = TokenUtils.getCurrentUser();
        Integer userId = user != null ? user.getId() : null;
        boolean ok = taskQueueService.rejectTask(taskId, userId);
        return ok ? Result.success("已拒绝") : Result.error(Constants.CODE_500, "拒绝失败，任务可能不存在或状态已变更");
    }

    /**
     * 手动执行任务
     */
    @PostMapping("/task/{taskId}/execute")
    public Result executeTask(@PathVariable String taskId) {
        if (taskQueueService == null) {
            return Result.error(Constants.CODE_500, "任务队列服务未配置");
        }
        boolean ok = taskQueueService.markExecuting(taskId);
        return ok ? Result.success("执行中") : Result.error(Constants.CODE_500, "执行失败，任务可能不存在或状态不允许");
    }

    // ══════════════════════════════════════════════════
    // 传感器事件接口
    // ══════════════════════════════════════════════════

    /**
     * 获取最近的传感器事件
     */
    @GetMapping("/sensor-events")
    public Result getSensorEvents(@RequestParam(defaultValue = "20") int limit,
                                  @RequestParam(required = false) Boolean handled) {
        if (sensorEventService == null) {
            return Result.error(Constants.CODE_500, "传感器事件服务未配置");
        }
        if (Boolean.TRUE.equals(handled)) {
            return Result.success(sensorEventService.getRecentEvents(limit));
        }
        return Result.success(sensorEventService.getUnhandledEvents(limit));
    }

    @Data
    private static class ExecuteRequest {
        private List<AgentAction> actions;
    }
}
