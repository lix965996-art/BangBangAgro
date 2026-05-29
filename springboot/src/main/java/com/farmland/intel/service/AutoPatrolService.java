package com.farmland.intel.service;

import com.farmland.intel.agent.AgentAction;
import com.farmland.intel.agent.AgentPlan;
import com.farmland.intel.entity.AutoPatrolLog;
import com.farmland.intel.entity.Notice;
import com.farmland.intel.entity.SensorEvent;
import com.farmland.intel.entity.Statistic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 无人农场自主巡检服务
 *
 * 三层架构：
 *   第一层 - 规则引擎：快速、可靠、无需 AI，检测阈值异常并立即执行操作
 *   第二层 - 传感器事件检测：将异常记录到 sensor_event 表，供 Agent 决策参考
 *   第三层 - Agent 自主决策：综合传感器事件 + RAG 知识，生成任务并评估风险
 */
@Service
@Slf4j
public class AutoPatrolService {

    // ── 配置项 ──
    @Value("${patrol.enabled:true}")
    private boolean patrolEnabledConfig;

    @Value("${patrol.soil-humidity-warn:25}")
    private int soilHumidityWarn;

    @Value("${patrol.temperature-warn:38}")
    private double temperatureWarn;

    @Value("${patrol.light-warn:500}")
    private int lightWarn;

    @Value("${agent.autonomous.enabled:false}")
    private boolean autonomousEnabled;

    // ── 运行时状态 ──
    private final AtomicBoolean patrolEnabled = new AtomicBoolean(true);
    private volatile LocalDateTime lastPatrolTime = null;
    private volatile int totalPatrols = 0;
    private volatile int totalActions = 0;

    // ── 依赖 ──
    @Autowired
    private IStatisticService statisticService;

    @Autowired(required = false)
    private IOneNetService oneNetService;

    @Autowired
    private IAutoPatrolLogService patrolLogService;

    @Autowired
    private AgentService agentService;

    @Autowired(required = false)
    private INoticeService noticeService;

    @Autowired(required = false)
    private ISensorEventService sensorEventService;

    @Autowired(required = false)
    private IAgentTaskQueueService taskQueueService;

    @Autowired(required = false)
    private ConfidenceEvaluator confidenceEvaluator;

    @PostConstruct
    public void init() {
        patrolEnabled.set(patrolEnabledConfig);
        log.info("【无人农场】巡检服务初始化完成 — 自主巡检: {}，Agent自主决策: {}，土壤湿度预警: {}%，温度预警: {}°C，光照预警: {} lux",
                patrolEnabled.get() ? "启用" : "禁用",
                autonomousEnabled ? "启用" : "禁用",
                soilHumidityWarn, temperatureWarn, lightWarn);
    }

    /**
     * 定时巡检任务，默认每30分钟执行一次，启动后60秒首次执行
     */
    @Scheduled(fixedDelayString = "${patrol.interval-ms:1800000}", initialDelay = 60000)
    public void runScheduledPatrol() {
        if (!patrolEnabled.get()) {
            log.debug("【无人农场】自主巡检已禁用，跳过本次定时巡检");
            return;
        }
        doPatrol("scheduled");
    }

    /**
     * 执行一次完整巡检（定时或手动触发）
     * @param triggerType "scheduled" | "manual"
     * @return 巡检结果摘要
     */
    public Map<String, Object> doPatrol(String triggerType) {
        log.info("【无人农场】开始{}巡检", "scheduled".equals(triggerType) ? "定时" : "手动");
        lastPatrolTime = LocalDateTime.now();
        totalPatrols++;

        List<AutoPatrolLog> allLogs = new ArrayList<>();
        int actionCount = 0;

        // ── 第一层：规则引擎 ──
        List<Statistic> farms = loadFarms();
        for (Statistic farm : farms) {
            List<AutoPatrolLog> farmLogs = applyRuleEngine(farm, triggerType);
            for (AutoPatrolLog l : farmLogs) {
                if ("success".equals(l.getResult())) actionCount++;
            }
            allLogs.addAll(farmLogs);
        }

        // 若没有任何规则触发，写一条"无需处置"记录
        if (allLogs.isEmpty() && !farms.isEmpty()) {
            AutoPatrolLog noAction = buildLog(triggerType, null, "no_action",
                    "所有农田状态正常，无需干预",
                    String.format("共巡检 %d 块农田，均在正常阈值范围内", farms.size()),
                    "no_action");
            allLogs.add(noAction);
        }

        // ── 第二层：传感器事件检测 ──
        List<SensorEvent> newEvents = detectSensorEvents(farms);
        if (!newEvents.isEmpty()) {
            log.info("【传感器事件】检测到 {} 个新事件", newEvents.size());
        }

        // ── 第三层：Agent 自主决策 ──
        if (autonomousEnabled && sensorEventService != null && taskQueueService != null) {
            try {
                int tasksCreated = runAgentDecisionLayer(farms, triggerType);
                if (tasksCreated > 0) {
                    actionCount += tasksCreated;
                    AutoPatrolLog agentLog = buildLog(triggerType, null, "agent_decision",
                            String.format("Agent 自主决策完成，创建 %d 个任务", tasksCreated),
                            null, "success");
                    allLogs.add(0, agentLog);
                }
            } catch (Exception e) {
                log.warn("【无人农场】Agent 自主决策失败（规则引擎结果不受影响）: {}", e.getMessage());
            }
        }

        // ── AI 综合分析 ──
        String aiReport = null;
        try {
            aiReport = runAiAnalysis(farms, triggerType);
        } catch (Exception e) {
            log.warn("【无人农场】AI 巡检分析失败（规则引擎结果不受影响）: {}", e.getMessage());
        }

        // 写入 AI 全局报告记录
        if (StringUtils.hasText(aiReport)) {
            AutoPatrolLog aiLog = buildLog(triggerType, null, "ai_analysis",
                    "AI 综合巡检分析完成", null, "success");
            aiLog.setAiReport(aiReport);
            allLogs.add(0, aiLog);
        }

        // ── 批量持久化日志 ──
        for (AutoPatrolLog pl : allLogs) {
            try {
                patrolLogService.save(pl);
            } catch (Exception e) {
                log.warn("【无人农场】保存巡检日志失败: {}", e.getMessage());
            }
        }

        totalActions += actionCount;
        log.info("【无人农场】{}巡检完成 — 农田: {}，触发操作: {}，新事件: {}，AI分析: {}",
                "scheduled".equals(triggerType) ? "定时" : "手动",
                farms.size(), actionCount, newEvents.size(), aiReport != null ? "完成" : "跳过");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("triggerType", triggerType);
        result.put("farmsChecked", farms.size());
        result.put("actionsExecuted", actionCount);
        result.put("newEvents", newEvents.size());
        result.put("aiReport", aiReport);
        result.put("logCount", allLogs.size());
        result.put("patrolTime", lastPatrolTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    // ══════════════════════════════════════════════════
    // 规则引擎：每块农田独立检测
    // ══════════════════════════════════════════════════

    private List<AutoPatrolLog> applyRuleEngine(Statistic farm, String triggerType) {
        List<AutoPatrolLog> logs = new ArrayList<>();
        String fn = farm.getFarm() != null ? farm.getFarm() : "未命名农田";

        // 规则1：土壤湿度过低 → 自动开灌溉
        Integer soilHum = farm.getSoilhumidity();
        if (soilHum != null && soilHum < soilHumidityWarn) {
            String reason = String.format("土壤湿度 %d%% 低于预警阈值 %d%%", soilHum, soilHumidityWarn);
            String res = irrigationOn(fn);
            logs.add(buildLog(triggerType, fn, "irrigation_on", "自动开启灌溉水泵", reason, res));
            log.info("【规则引擎】{} — {} → {}", fn, reason, res);
        }

        // 规则2：高温预警 → 发系统通知
        if (farm.getTemperature() != null && farm.getTemperature().doubleValue() > temperatureWarn) {
            double temp = farm.getTemperature().doubleValue();
            String reason = String.format("温度 %.1f°C 超过预警阈值 %.0f°C", temp, temperatureWarn);
            String res = pushNotice(fn, "高温预警", reason);
            logs.add(buildLog(triggerType, fn, "send_notification", "推送高温预警通知", reason, res));
            log.info("【规则引擎】{} — {} → {}", fn, reason, res);
        }

        // 规则3：白天光照不足 → 自动开补光灯
        int hour = LocalDateTime.now().getHour();
        Integer light = farm.getLight();
        if (light != null && light < lightWarn && hour >= 6 && hour <= 18) {
            String reason = String.format("光照 %d lux 低于预警阈值 %d lux（%d时）", light, lightWarn, hour);
            String res = ledOn(fn);
            logs.add(buildLog(triggerType, fn, "led_on", "自动开启补光灯", reason, res));
            log.info("【规则引擎】{} — {} → {}", fn, reason, res);
        }

        return logs;
    }

    // ══════════════════════════════════════════════════
    // 传感器事件检测层
    // ══════════════════════════════════════════════════

    private List<SensorEvent> detectSensorEvents(List<Statistic> farms) {
        List<SensorEvent> allEvents = new ArrayList<>();
        if (sensorEventService == null) return allEvents;

        for (Statistic farm : farms) {
            String fn = farm.getFarm() != null ? farm.getFarm() : "未命名农田";

            if (farm.getSoilhumidity() != null) {
                allEvents.addAll(sensorEventService.detectEvents(fn, "soil_humidity",
                        BigDecimal.valueOf(farm.getSoilhumidity())));
            }
            if (farm.getTemperature() != null) {
                allEvents.addAll(sensorEventService.detectEvents(fn, "temperature",
                        farm.getTemperature()));
            }
            if (farm.getLight() != null) {
                allEvents.addAll(sensorEventService.detectEvents(fn, "light",
                        BigDecimal.valueOf(farm.getLight())));
            }
            if (farm.getAirhumidity() != null) {
                allEvents.addAll(sensorEventService.detectEvents(fn, "air_humidity",
                        BigDecimal.valueOf(farm.getAirhumidity())));
            }
        }
        return allEvents;
    }

    // ══════════════════════════════════════════════════
    // Agent 自主决策层
    // ══════════════════════════════════════════════════

    /**
     * 收集未处理的传感器事件，调用 Agent 生成决策，评估风险后创建任务
     * @return 创建的任务数
     */
    private int runAgentDecisionLayer(List<Statistic> farms, String triggerType) {
        // 1. 获取未处理的传感器事件
        List<SensorEvent> unhandledEvents = sensorEventService.getUnhandledEvents(50);
        if (unhandledEvents.isEmpty()) {
            log.debug("【Agent决策】无未处理的传感器事件，跳过");
            return 0;
        }

        // 2. 构建 Agent 提示
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是无人农场的自主决策 Agent。当前检测到以下传感器异常事件，请分析并给出处理建议：\n\n");

        for (SensorEvent event : unhandledEvents) {
            prompt.append(String.format("- 农田[%s] %s 当前值=%s 阈值=%s 严重程度=%s\n",
                    event.getFarmName(), event.getMetricName(),
                    event.getCurrentValue(), event.getThresholdValue(), event.getSeverity()));
        }

        prompt.append("\n请根据事件严重程度，给出具体的操作建议（如灌溉、补光、通知等）。");

        // 3. 调用 Agent 生成决策
        AgentPlan plan;
        try {
            plan = agentService.buildPlan(null, prompt.toString(), null, "agent", "sensor_event");
        } catch (Exception e) {
            log.warn("【Agent决策】调用 Agent 失败: {}", e.getMessage());
            return 0;
        }

        if (plan == null || !StringUtils.hasText(plan.getAdvice())) {
            log.debug("【Agent决策】Agent 未返回有效建议");
            return 0;
        }

        log.info("【Agent决策】Agent 建议: {}", truncate(plan.getAdvice(), 200));

        // 4. 解析 Agent 的动作建议，创建任务
        int tasksCreated = 0;
        List<AgentAction> actions = plan.getActions();
        if (actions != null && !actions.isEmpty()) {
            for (AgentAction action : actions) {
                try {
                    String actionType = action.getType();
                    String riskLevel = action.getRiskLevel();

                    // 使用 ConfidenceEvaluator 评估风险
                    ConfidenceEvaluator.EvaluationResult evaluation = null;
                    if (confidenceEvaluator != null) {
                        // 从事件中找到对应的严重程度
                        String severity = findSeverityForAction(unhandledEvents, action);
                        evaluation = confidenceEvaluator.evaluate(
                                actionType, severity, null, null, null);
                        riskLevel = evaluation.getRiskLevel();
                    }

                    boolean autoExec = evaluation != null && evaluation.isAutoExecute();
                    BigDecimal confidence = evaluation != null ? evaluation.getConfidenceScore() : null;
                    String reasoning = evaluation != null ? evaluation.getReasoning() : "Agent建议";

                    // 创建任务
                    if (taskQueueService != null) {
                        taskQueueService.createTask(
                                plan.getChainId(),
                                inferTaskType(actionType),
                                action.getRiskLevel() != null ? action.getRiskLevel() : "medium",
                                riskLevel,
                                autoExec,
                                action.getTarget(),
                                actionType,
                                action.getParams() != null ? cn.hutool.json.JSONUtil.toJsonStr(action.getParams()) : null,
                                reasoning,
                                null,
                                confidence
                        );
                        tasksCreated++;

                        // 标记相关事件已处理
                        List<Long> eventIds = getRelatedEventIds(unhandledEvents, action);
                        sensorEventService.markAllHandled(eventIds, plan.getChainId());
                    }
                } catch (Exception e) {
                    log.warn("【Agent决策】创建任务失败: {}", e.getMessage());
                }
            }
        } else {
            // Agent 没有给出具体动作，仅记录建议
            log.info("【Agent决策】Agent 仅给出建议，无具体动作: {}", truncate(plan.getAdvice(), 100));
            // 标记所有事件已处理（Agent 已分析过）
            List<Long> allIds = new ArrayList<>();
            for (SensorEvent e : unhandledEvents) {
                allIds.add(e.getId());
            }
            sensorEventService.markAllHandled(allIds, plan.getChainId());
        }

        return tasksCreated;
    }

    /**
     * 从动作类型推断任务类型
     */
    private String inferTaskType(String actionType) {
        if (actionType == null) return "inspection";
        if (actionType.contains("irrigation")) return "irrigation";
        if (actionType.contains("led")) return "led";
        if (actionType.contains("notification") || actionType.contains("notice")) return "notification";
        if (actionType.contains("purchase")) return "purchase";
        return "inspection";
    }

    /**
     * 从事件列表中找到与动作关联的严重程度
     */
    private String findSeverityForAction(List<SensorEvent> events, AgentAction action) {
        if (action.getTarget() == null && events.isEmpty()) return null;
        for (SensorEvent event : events) {
            if (action.getTarget() != null && action.getTarget().equals(event.getFarmName())) {
                return event.getSeverity();
            }
        }
        // 返回最严重的事件
        String maxSeverity = "low";
        for (SensorEvent event : events) {
            if ("critical".equals(event.getSeverity())) return "critical";
            if ("high".equals(event.getSeverity())) maxSeverity = "high";
            else if ("medium".equals(event.getSeverity()) && "low".equals(maxSeverity)) maxSeverity = "medium";
        }
        return maxSeverity;
    }

    /**
     * 获取与动作关联的事件ID
     */
    private List<Long> getRelatedEventIds(List<SensorEvent> events, AgentAction action) {
        List<Long> ids = new ArrayList<>();
        for (SensorEvent event : events) {
            if (action.getTarget() != null && action.getTarget().equals(event.getFarmName())) {
                ids.add(event.getId());
            }
        }
        // 如果没有匹配到具体农田，返回所有事件ID
        if (ids.isEmpty()) {
            for (SensorEvent event : events) {
                ids.add(event.getId());
            }
        }
        return ids;
    }

    // ══════════════════════════════════════════════════
    // AI 综合分析层
    // ══════════════════════════════════════════════════

    private String runAiAnalysis(List<Statistic> farms, String triggerType) {
        if (farms.isEmpty()) return null;

        String patrolPrompt =
            "请对帮帮农农场执行一次自主巡检（触发方式：" +
            ("scheduled".equals(triggerType) ? "定时巡检" : "手动触发") + "）：\n" +
            "1. 调用 get_all_farms 获取全部农田当前传感器数据\n" +
            "2. 检查是否有需要额外干预的异常（规则引擎已处理湿度/高温/光照，请关注其他异常）\n" +
            "3. 生成一份简洁的巡检报告，格式：「整体状态：xx；异常项：xx；建议：xx」，控制在80字以内";

        try {
            AgentPlan plan = agentService.buildPlan(null, patrolPrompt, null, "agent", "auto_patrol");
            if (plan != null && StringUtils.hasText(plan.getAdvice())) {
                return plan.getAdvice();
            }
        } catch (Exception e) {
            log.debug("AI 巡检分析异常: {}", e.getMessage());
        }
        return null;
    }

    // ══════════════════════════════════════════════════
    // 设备控制 & 通知
    // ══════════════════════════════════════════════════

    private String irrigationOn(String farmName) {
        try {
            if (oneNetService != null) {
                return oneNetService.controlBump(true) ? "success" : "failed";
            }
            return "skipped";  // OneNET 未配置
        } catch (Exception e) {
            log.warn("灌溉控制异常 [{}]: {}", farmName, e.getMessage());
            return "failed";
        }
    }

    private String ledOn(String farmName) {
        try {
            if (oneNetService != null) {
                return oneNetService.controlLed(true) ? "success" : "failed";
            }
            return "skipped";
        } catch (Exception e) {
            log.warn("补光灯控制异常 [{}]: {}", farmName, e.getMessage());
            return "failed";
        }
    }

    private String pushNotice(String farmName, String title, String content) {
        try {
            if (noticeService != null) {
                Notice notice = new Notice();
                notice.setName("[自动预警] " + farmName + " · " + title);
                notice.setContent(content);
                notice.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                notice.setUser("巡检系统");
                noticeService.save(notice);
                return "success";
            }
            return "skipped";
        } catch (Exception e) {
            log.warn("推送通知异常 [{}]: {}", farmName, e.getMessage());
            return "failed";
        }
    }

    // ══════════════════════════════════════════════════
    // 工具方法
    // ══════════════════════════════════════════════════

    private List<Statistic> loadFarms() {
        try {
            List<Statistic> farms = statisticService.list();
            return farms != null ? farms : Collections.emptyList();
        } catch (Exception e) {
            log.warn("读取农田数据失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private AutoPatrolLog buildLog(String triggerType, String farmName,
                                    String actionType, String actionDetail,
                                    String reason, String result) {
        AutoPatrolLog log = new AutoPatrolLog();
        log.setPatrolTime(new Date());
        log.setTriggerType(triggerType);
        log.setFarmName(farmName);
        log.setActionType(actionType);
        log.setActionDetail(actionDetail);
        log.setReason(reason);
        log.setResult(result);
        return log;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // ══════════════════════════════════════════════════
    // 状态查询 & 控制（供 Controller 调用）
    // ══════════════════════════════════════════════════

    public boolean isPatrolEnabled() { return patrolEnabled.get(); }

    /** 切换启用/禁用，返回切换后的状态 */
    public boolean togglePatrol() {
        boolean newState = !patrolEnabled.get();
        patrolEnabled.set(newState);
        log.info("【无人农场】自主巡检已{}", newState ? "启用" : "禁用");
        return newState;
    }

    public boolean isAutonomousEnabled() { return autonomousEnabled; }

    public LocalDateTime getLastPatrolTime() { return lastPatrolTime; }
    public int getTotalPatrols()             { return totalPatrols; }
    public int getTotalActions()             { return totalActions; }
}
