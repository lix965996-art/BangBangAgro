package com.farmland.intel.controller;

import com.farmland.intel.agent.AgentAction;
import com.farmland.intel.agent.AgentActionResult;
import com.farmland.intel.agent.AgentPlan;
import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.AgentUserMemory;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.AgentService;
import com.farmland.intel.service.IAgentUserMemoryService;
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
@CrossOrigin
@Slf4j
public class AgentController {

    @Autowired
    private AgentService agentService;

    @Autowired(required = false)
    private IAgentUserMemoryService agentUserMemoryService;

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
        }

        AgentPlan plan;
        try {
            plan = agentService.buildPlan(userId, question, history);
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

    @Data
    private static class ExecuteRequest {
        private List<AgentAction> actions;
    }
}
