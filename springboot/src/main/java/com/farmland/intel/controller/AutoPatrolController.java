package com.farmland.intel.controller;

import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.AutoPatrolLog;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.AutoPatrolService;
import com.farmland.intel.service.IAutoPatrolLogService;
import com.farmland.intel.utils.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 无人农场自主巡检接口
 *
 * GET  /api/patrol/status   → 获取巡检状态（启用/禁用、统计数据）
 * POST /api/patrol/toggle   → 开启/关闭自主巡检
 * POST /api/patrol/trigger  → 手动触发一次巡检
 * GET  /api/patrol/logs     → 查询巡检日志（默认最近50条）
 */
@RestController
@RequestMapping("/api/patrol")
@Slf4j
public class AutoPatrolController {

    @Autowired
    private AutoPatrolService autoPatrolService;

    @Autowired
    private IAutoPatrolLogService patrolLogService;

    /** 获取巡检运行状态 */
    @GetMapping("/status")
    public Result getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", autoPatrolService.isPatrolEnabled());
        status.put("totalPatrols", autoPatrolService.getTotalPatrols());
        status.put("totalActions", autoPatrolService.getTotalActions());

        java.time.LocalDateTime lastTime = autoPatrolService.getLastPatrolTime();
        status.put("lastPatrolTime", lastTime != null
                ? lastTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);

        // 最近一条 AI 报告
        List<AutoPatrolLog> recent = patrolLogService.getRecentLogs(10);
        String latestReport = recent.stream()
                .filter(l -> "ai_analysis".equals(l.getActionType()) && l.getAiReport() != null)
                .findFirst()
                .map(AutoPatrolLog::getAiReport)
                .orElse(null);
        status.put("latestAiReport", latestReport);

        return Result.success(status);
    }

    /** 开启 / 关闭自主巡检 */
    @PostMapping("/toggle")
    public Result toggle() {
        User user = TokenUtils.getCurrentUser();
        if (user == null) return Result.error(Constants.CODE_401, "未登录");

        boolean newState = autoPatrolService.togglePatrol();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("enabled", newState);
        res.put("message", newState ? "自主巡检已开启 ✅" : "自主巡检已暂停 ⏸");
        return Result.success(res);
    }

    /** 手动触发一次巡检 */
    @PostMapping("/trigger")
    public Result trigger() {
        User user = TokenUtils.getCurrentUser();
        if (user == null) return Result.error(Constants.CODE_401, "未登录");

        log.info("用户 {} 手动触发巡检", user.getUsername());
        try {
            Map<String, Object> result = autoPatrolService.doPatrol("manual");
            return Result.success(result);
        } catch (Exception e) {
            log.error("手动巡检执行失败", e);
            return Result.error(Constants.CODE_500, "巡检执行失败：" + e.getMessage());
        }
    }

    /** 查询巡检日志 */
    @GetMapping("/logs")
    public Result getLogs(@RequestParam(defaultValue = "50") int limit) {
        List<AutoPatrolLog> logs = patrolLogService.getRecentLogs(limit);
        return Result.success(logs);
    }
}
