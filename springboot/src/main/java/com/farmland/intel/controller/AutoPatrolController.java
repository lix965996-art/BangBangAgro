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

    @Autowired(required = false)
    private com.farmland.intel.service.IAiConfigService aiConfigService;

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

        // 最近一条 AI 报告：只取当前登录用户用「自己的 key」跑出来的（operator_id = 本人）。
        // 这样别人的报告、以及历史全局脏数据(operator_id=null)都不会串到当前用户。
        User currentUser = TokenUtils.getCurrentUser();
        String latestReport = null;
        boolean aiConfigured = false;
        if (currentUser != null) {
            AutoPatrolLog mine = patrolLogService.lambdaQuery()
                    .eq(AutoPatrolLog::getActionType, "ai_analysis")
                    .eq(AutoPatrolLog::getOperatorId, currentUser.getId())
                    .isNotNull(AutoPatrolLog::getAiReport)
                    .orderByDesc(AutoPatrolLog::getPatrolTime)
                    .last("limit 1")
                    .one();
            if (mine != null) latestReport = mine.getAiReport();

            if (aiConfigService != null) {
                com.farmland.intel.entity.AiConfig cfg = aiConfigService.getByUserId(currentUser.getId());
                aiConfigured = cfg != null && org.springframework.util.StringUtils.hasText(cfg.getApiKey());
            }
        }
        status.put("latestAiReport", latestReport);
        status.put("aiConfigured", aiConfigured);  // 前端据此提示用户去配 key

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
            Map<String, Object> result = autoPatrolService.doPatrol("manual", user.getId());
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

    /**
     * 一键清理被 fallbackPlan 污染的历史 AI 巡检报告。
     *
     * 历史背景:旧版本的 runAiAnalysis 在 LLM 不可用时会走 1000 行 if-else 兜底,
     * 把巡检 prompt 误识别成对话场景,返回"为您打开 XX 界面"之类的话术,
     * 写入了 auto_patrol_log.ai_report 字段。
     *
     * 该接口扫描所有 action_type='ai_analysis' 的日志,把符合污染特征的
     * 报告内容置为 null(保留日志记录本身,只清掉脏字段)。
     *
     * 调用方式: 浏览器访问 POST /api/patrol/cleanup-ai-reports (或 Postman)
     * 返回: { code: "200", data: { scanned: N, cleaned: M } }
     */
    @PostMapping("/cleanup-ai-reports")
    public Result cleanupCorruptedAiReports() {
        // 已知的 fallbackPlan 污染特征短语
        String[] patterns = {
            "为您打开", "已为您", "为您切换", "数据监控界面",
            "我是「禾序」", "您可以前往", "好的，为您"
        };

        List<AutoPatrolLog> all = patrolLogService.lambdaQuery()
                .eq(AutoPatrolLog::getActionType, "ai_analysis")
                .isNotNull(AutoPatrolLog::getAiReport)
                .list();

        int scanned = all.size();
        int cleaned = 0;
        for (AutoPatrolLog logEntry : all) {
            String ai = logEntry.getAiReport();
            if (ai == null) continue;
            boolean dirty = false;
            for (String p : patterns) {
                if (ai.contains(p)) { dirty = true; break; }
            }
            if (dirty) {
                logEntry.setAiReport(null);
                patrolLogService.updateById(logEntry);
                cleaned++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", scanned);
        result.put("cleaned", cleaned);
        log.info("[巡检日志清理] 扫描 {} 条,清理污染 AI 报告 {} 条", scanned, cleaned);
        return Result.success(result);
    }
}
