package com.farmland.intel.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.AutoPatrolLog;
import com.farmland.intel.service.IAutoPatrolLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 量化成果统计接口 — 申报书"应用价值"和答辩演示的核心数据弹药。
 *
 * 所有数据**真实来自数据库聚合**,没有 mock:
 * - 累计运行天数: 从最早的 auto_patrol_log.patrol_time 算起
 * - Agent 自主决策次数: agent_decision_chain 表行数
 * - 自动操作触发次数: auto_patrol_log 按 action_type 分组统计
 * - 自动灌溉/补光灯次数: 同上,按动作类型筛选
 * - 异步事件巡检次数: trigger_type='event_driven' 的日志条数(展示 P1 改造效果)
 * - 节水换算: 灌溉次数 × 配置的单次水量(展示量化效益)
 *
 * 端点: GET /api/dashboard/achievements
 *
 * 返回结构示例:
 * {
 *   "runDays": 14,
 *   "totalPatrols": 672,
 *   "eventDrivenPatrols": 645,           // P1 改造贡献,延迟从 30min→秒级
 *   "autoActions": 47,
 *   "autoIrrigation": 12,
 *   "autoLed": 28,
 *   "autoNotifications": 7,
 *   "agentDecisions": 33,
 *   "estimatedWaterSavedLiter": 2400,    // 12 次 × 200L
 *   "estimatedLaborSavedHour": 8         // 按 (autoActions × 10min / 60) 估算
 * }
 */
@RestController
@RequestMapping("/api/dashboard")
@Slf4j
@Tag(name = "量化成果统计", description = "v1.1.0 新增 - 申报书/答辩演示的数据弹药")
public class AchievementController {

    @Autowired
    private IAutoPatrolLogService patrolLogService;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /** 单次自动灌溉的估算水量(升) */
    private static final int WATER_PER_IRRIGATION_L = 200;

    /** 每次自动操作折算节省人力(分钟) */
    private static final int LABOR_SAVED_PER_ACTION_MIN = 10;

    @Operation(
        summary = "运行成果聚合统计",
        description = "返回累计运行天数、Agent 决策次数、自动操作次数、节水/省人力换算等指标。" +
                "全部基于真实数据库聚合(2 次 GROUP BY 一次性算出)。" +
                "特别突出 eventDrivenPatrols 字段,展示 P1 改造贡献。"
    )
    @GetMapping("/achievements")
    public Result achievements() {
        Map<String, Object> data = new LinkedHashMap<>();

        // P0 性能优化: 原代码 8 次单独 count() 查同一张表,改为 2 次 GROUP BY 一次性聚合
        Map<String, Long> actionCounts = countByActionGrouped();
        Map<String, Long> triggerCounts = countByTriggerGrouped();

        long totalPatrols = actionCounts.values().stream().mapToLong(Long::longValue).sum();
        long eventDrivenPatrols = triggerCounts.getOrDefault("event_driven", 0L);
        long autoIrrigation = actionCounts.getOrDefault("irrigation_on", 0L);
        long autoLed = actionCounts.getOrDefault("led_on", 0L);
        long autoNotifications = actionCounts.getOrDefault("send_notification", 0L);
        long agentDecisions = actionCounts.getOrDefault("agent_decision", 0L);
        long aiAnalysis = actionCounts.getOrDefault("ai_analysis", 0L);

        // 自动操作次数 = 总数 - 无操作 - AI 报告
        long autoActions = totalPatrols
                - actionCounts.getOrDefault("no_action", 0L)
                - aiAnalysis;

        // 累计运行天数
        long runDays = computeRunDays();

        // 量化效益换算
        long waterSavedL = autoIrrigation * WATER_PER_IRRIGATION_L;
        long laborSavedH = autoActions * LABOR_SAVED_PER_ACTION_MIN / 60;

        // 其他表统计(安全 fallback)
        long agentChainCount = safeCountTable("agent_decision_chain");
        long alertCount = safeCountTable("farmland_alert");
        long sensorReadingCount = safeCountTable("sensor_reading");

        data.put("runDays", runDays);
        data.put("totalPatrols", totalPatrols);
        data.put("eventDrivenPatrols", eventDrivenPatrols);
        data.put("autoActions", autoActions);
        data.put("autoIrrigation", autoIrrigation);
        data.put("autoLed", autoLed);
        data.put("autoNotifications", autoNotifications);
        data.put("agentDecisions", agentDecisions);
        data.put("aiAnalysisReports", aiAnalysis);
        data.put("agentChainCount", agentChainCount);
        data.put("alertCount", alertCount);
        data.put("sensorReadingCount", sensorReadingCount);
        data.put("estimatedWaterSavedLiter", waterSavedL);
        data.put("estimatedLaborSavedHour", laborSavedH);

        return Result.success(data);
    }

    /** 一次 SQL GROUP BY action_type,返回 {action_type → count} */
    private Map<String, Long> countByActionGrouped() {
        Map<String, Long> result = new LinkedHashMap<>();
        if (jdbcTemplate == null) return result;
        try {
            jdbcTemplate.query(
                "SELECT action_type, COUNT(*) AS cnt FROM auto_patrol_log GROUP BY action_type",
                rs -> {
                    String type = rs.getString("action_type");
                    if (type != null) result.put(type, rs.getLong("cnt"));
                });
        } catch (Exception e) {
            log.debug("[Achievement] action_type 聚合失败: {}", e.getMessage());
        }
        return result;
    }

    /** 一次 SQL GROUP BY trigger_type */
    private Map<String, Long> countByTriggerGrouped() {
        Map<String, Long> result = new LinkedHashMap<>();
        if (jdbcTemplate == null) return result;
        try {
            jdbcTemplate.query(
                "SELECT trigger_type, COUNT(*) AS cnt FROM auto_patrol_log GROUP BY trigger_type",
                rs -> {
                    String type = rs.getString("trigger_type");
                    if (type != null) result.put(type, rs.getLong("cnt"));
                });
        } catch (Exception e) {
            log.debug("[Achievement] trigger_type 聚合失败: {}", e.getMessage());
        }
        return result;
    }

    private long computeRunDays() {
        try {
            List<AutoPatrolLog> first = patrolLogService.list(
                    Wrappers.<AutoPatrolLog>lambdaQuery()
                            .orderByAsc(AutoPatrolLog::getPatrolTime)
                            .last("LIMIT 1"));
            if (first.isEmpty() || first.get(0).getPatrolTime() == null) {
                return 0;
            }
            java.util.Date firstDate = first.get(0).getPatrolTime();
            LocalDateTime firstLdt = LocalDateTime.ofInstant(firstDate.toInstant(),
                    java.time.ZoneId.systemDefault());
            Duration d = Duration.between(firstLdt, LocalDateTime.now());
            return Math.max(0, d.toDays());
        } catch (Exception e) {
            log.debug("计算运行天数失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 安全查表行数 — 表不存在时返回 0 不抛错 */
    private long safeCountTable(String tableName) {
        if (jdbcTemplate == null) return 0;
        try {
            Long n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName, Long.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            log.debug("统计表 {} 失败: {}", tableName, e.getMessage());
            return 0;
        }
    }
}
