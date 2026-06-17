package com.farmland.intel.service;

import com.farmland.intel.agent.AgentAction;
import com.farmland.intel.agent.AgentPlan;
import com.farmland.intel.entity.AutoPatrolLog;
import com.farmland.intel.entity.Notice;
import com.farmland.intel.entity.SensorEvent;
import com.farmland.intel.entity.Statistic;
import com.farmland.intel.event.SensorDataSyncedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** P1 改造: 是否启用事件驱动巡检(传感器数据更新后立即跑规则引擎) */
    @Value("${patrol.event-driven.enabled:true}")
    private boolean eventDrivenEnabled;

    /** P1 改造: 事件驱动巡检的最短间隔(毫秒),防止传感器风暴 */
    @Value("${patrol.event-driven.cooldown-ms:25000}")
    private long eventDrivenCooldownMs;

    // ── 运行时状态 ──
    private final AtomicBoolean patrolEnabled = new AtomicBoolean(true);
    private volatile LocalDateTime lastPatrolTime = null;
    private final AtomicInteger totalPatrols = new AtomicInteger(0);
    private final AtomicInteger totalActions = new AtomicInteger(0);

    /** P1 改造: 上次事件驱动巡检的时间戳,用于冷却去重 */
    private volatile long lastEventDrivenPatrolMs = 0L;
    /** P1 改造: 事件驱动巡检累计次数(运维观察用) */
    private volatile int eventDrivenPatrolCount = 0;

    /** M2: 自动巡检统一并发守卫——定时与事件驱动互斥执行,避免两者重叠重复下发设备命令/重复任务。手动触发不受此限。 */
    private final AtomicBoolean patrolBusy = new AtomicBoolean(false);

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
    private IAiConfigService aiConfigService;

    @Autowired(required = false)
    private INoticeService noticeService;

    @Autowired(required = false)
    private ISensorEventService sensorEventService;

    @Autowired(required = false)
    private IAgentTaskQueueService taskQueueService;

    @Autowired(required = false)
    private ConfidenceEvaluator confidenceEvaluator;

    /** P1 改造: self-injection 用于跨方法调 @Async,this. 自调用会绕过代理导致 @Async 失效 */
    @Autowired
    @Lazy
    private AutoPatrolService self;

    @PostConstruct
    public void init() {
        patrolEnabled.set(patrolEnabledConfig);
        log.info("【无人农场】巡检服务初始化完成 — 自主巡检: {}，Agent自主决策: {}，土壤湿度预警: {}%，温度预警: {}°C，光照预警: {} lux",
                patrolEnabled.get() ? "启用" : "禁用",
                autonomousEnabled ? "启用" : "禁用",
                soilHumidityWarn, temperatureWarn, lightWarn);
    }

    /**
     * 定时巡检任务，默认每30分钟执行一次，启动后60秒首次执行。
     * P1 改造后,这条线路退化为"兜底"——主线路是下面的事件驱动巡检。
     */
    @Scheduled(fixedDelayString = "${patrol.interval-ms:1800000}", initialDelay = 60000)
    public void runScheduledPatrol() {
        if (!patrolEnabled.get()) {
            log.debug("【无人农场】自主巡检已禁用，跳过本次定时巡检");
            return;
        }
        // M2: 与事件驱动巡检互斥，已有巡检在执行就跳过本次定时兜底
        if (!patrolBusy.compareAndSet(false, true)) {
            log.debug("【无人农场】已有巡检在执行，跳过本次定时巡检");
            return;
        }
        try {
            doPatrol("scheduled");
        } finally {
            patrolBusy.set(false);
        }
    }

    /**
     * P1 改造: 事件驱动巡检入口。
     *
     * 由 {@link com.farmland.intel.config.ScheduledTasks#syncOneNetData()}
     * 每 30 秒同步完 OneNET 传感器数据后发布 {@link SensorDataSyncedEvent},
     * 触发本方法。只跑 "第一层规则引擎",不跑 LLM 分析(那个走异步定时)。
     *
     * 特性:
     * - 冷却去重: {@code patrol.event-driven.cooldown-ms} 内最多触发一次,防止传感器风暴
     * - 异步执行: 不阻塞 OneNET 数据同步主线程
     * - 失败兜底: 抛错不影响 {@link #runScheduledPatrol()} 定时兜底巡检
     * - 关键开关: {@code patrol.event-driven.enabled=false} 可一键回退到老的纯定时模式
     */
    @Async
    @EventListener
    public void onSensorDataSynced(SensorDataSyncedEvent event) {
        if (!patrolEnabled.get() || !eventDrivenEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastEventDrivenPatrolMs < eventDrivenCooldownMs) {
            log.debug("【事件驱动巡检】冷却中(剩余 {} ms),跳过本次", eventDrivenCooldownMs - (now - lastEventDrivenPatrolMs));
            return;
        }
        // M2: 与定时巡检互斥，已有巡检在执行就跳过，避免重复下发设备命令
        if (!patrolBusy.compareAndSet(false, true)) {
            log.debug("【事件驱动巡检】已有巡检在执行，跳过本次");
            return;
        }
        lastEventDrivenPatrolMs = now;
        eventDrivenPatrolCount++;

        try {
            log.info("【事件驱动巡检】#{} 触发(source={}),执行轻量规则引擎",
                    eventDrivenPatrolCount, event.getSourceLabel());
            List<Statistic> farms = loadFarms();
            List<AutoPatrolLog> logs = new ArrayList<>();
            int actionCount = 0;
            for (Statistic farm : farms) {
                List<AutoPatrolLog> farmLogs = applyRuleEngine(farm, "event_driven");
                for (AutoPatrolLog l : farmLogs) {
                    if ("success".equals(l.getResult())) actionCount++;
                }
                logs.addAll(farmLogs);
            }
            if (!logs.isEmpty()) {
                // P1 顺手优化: 用 saveBatch 替代单条 save 循环
                try {
                    patrolLogService.saveBatch(logs);
                } catch (Exception e) {
                    log.warn("【事件驱动巡检】saveBatch 失败,降级单条 save: {}", e.getMessage());
                    for (AutoPatrolLog pl : logs) {
                        try { patrolLogService.save(pl); } catch (Exception ignore) {}
                    }
                }
            }
            totalActions.addAndGet(actionCount);
            log.info("【事件驱动巡检】#{} 完成 — 农田: {},触发操作: {}",
                    eventDrivenPatrolCount, farms.size(), actionCount);
        } catch (Exception e) {
            log.warn("【事件驱动巡检】#{} 失败(不影响定时兜底): {}", eventDrivenPatrolCount, e.getMessage());
        } finally {
            patrolBusy.set(false);  // M2: 始终释放守卫，防异常卡死
        }
    }

    /**
     * 执行一次完整巡检（定时或手动触发）
     * @param triggerType "scheduled" | "manual"
     * @return 巡检结果摘要
     */
    public Map<String, Object> doPatrol(String triggerType) {
        return doPatrol(triggerType, null);
    }

    /**
     * 执行一次完整巡检（定时或手动触发）
     * @param triggerType "scheduled" | "manual" | "event_driven"
     * @param operatorId  手动触发时的操作员 sys_user.id (评分归因用); 其它触发为 null
     * @return 巡检结果摘要
     */
    public Map<String, Object> doPatrol(String triggerType, Integer operatorId) {
        log.info("【无人农场】开始{}巡检", "scheduled".equals(triggerType) ? "定时" : "手动");
        lastPatrolTime = LocalDateTime.now();
        totalPatrols.incrementAndGet();

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

        // ── AI 综合分析 (P1 改造: 改异步,不阻塞主流程几十秒) ──
        // 必须通过 self 代理调用,否则 this. 自调用会绕过 Spring AOP 让 @Async 失效
        // operatorId: 手动触发=登录用户(用其 key)；定时/事件触发=null(系统 key 兜底,没配则跳过)
        self.triggerAiAnalysisAsync(farms, triggerType, operatorId);
        String aiReport = null; // 主流程已不持有该值,保留变量仅为下方日志格式兼容

        // ── 给本次巡检产出的所有日志打上操作员 (仅手动触发, 用于周评分归因) ──
        if ("manual".equals(triggerType) && operatorId != null) {
            allLogs.forEach(l -> l.setOperatorId(operatorId));
        }

        // ── 批量持久化日志 (P3 改造: 单条 save 循环 → saveBatch,减少 N-1 次数据库往返) ──
        if (!allLogs.isEmpty()) {
            try {
                patrolLogService.saveBatch(allLogs);
            } catch (Exception e) {
                log.warn("【无人农场】saveBatch 失败,降级单条 save: {}", e.getMessage());
                for (AutoPatrolLog pl : allLogs) {
                    try { patrolLogService.save(pl); } catch (Exception ignore) {}
                }
            }
        }

        totalActions.addAndGet(actionCount);
        log.info("【无人农场】{}巡检完成 — 农田: {}，触发操作: {}，新事件: {}，AI分析: 已异步触发",
                "scheduled".equals(triggerType) ? "定时" : "手动",
                farms.size(), actionCount, newEvents.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("triggerType", triggerType);
        result.put("farmsChecked", farms.size());
        result.put("actionsExecuted", actionCount);
        result.put("newEvents", newEvents.size());
        result.put("aiReport", "AI 综合分析已异步触发,完成后写入巡检日志,可在日志列表查看"); // P1 改造: 主流程不再阻塞
        result.put("logCount", allLogs.size());
        result.put("patrolTime", lastPatrolTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    /**
     * P1 改造: 异步触发 AI 综合分析,完成后自己写日志,不阻塞 doPatrol 主流程。
     * 失败也只打 warn,不影响巡检结果。
     */
    @Async
    public void triggerAiAnalysisAsync(List<Statistic> farms, String triggerType, Integer userId) {
        try {
            String report = runAiAnalysis(farms, triggerType, userId);
            if (StringUtils.hasText(report)) {
                AutoPatrolLog aiLog = buildLog(triggerType, null, "ai_analysis",
                        "AI 综合巡检分析完成(异步)", null, "success");
                aiLog.setAiReport(report);
                // 打上发起用户：该报告只属于他（用他的 key 跑的），status 接口按此过滤
                aiLog.setOperatorId(userId);
                try {
                    patrolLogService.save(aiLog);
                } catch (Exception e) {
                    log.warn("【AI 异步分析】写日志失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("【AI 异步分析】调用失败(不影响主巡检): {}", e.getMessage());
        }
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

    /**
     * AI 综合分析。
     * @param userId 用谁的 AI key 跑分析：手动触发时=登录用户 id（用其个人中心配置的 key）；
     *               后台定时/事件触发=null（无登录用户，降级用系统 yml key，没配则跳过）。
     */
    private String runAiAnalysis(List<Statistic> farms, String triggerType, Integer userId) {
        if (farms.isEmpty()) return null;

        // ── 多租户硬性约束：只用「用户本人配置的 key」，绝不用公共/系统 key ──
        // 1. 后台无人值守(userId=null)：没有登录用户 → 不跑 AI(避免误用系统兜底 key)
        if (userId == null) {
            log.debug("[auto_patrol] 无登录用户(后台触发),跳过 AI 分析(多租户不使用公共 key)");
            return null;
        }
        // 2. 该用户没配自己的 key → 不跑 AI(前端会提示去个人中心配置)
        if (aiConfigService != null) {
            com.farmland.intel.entity.AiConfig cfg = aiConfigService.getByUserId(userId);
            if (cfg == null || !StringUtils.hasText(cfg.getApiKey())) {
                log.debug("[auto_patrol] 用户 {} 未配置自己的 AI key,跳过 AI 分析", userId);
                return null;
            }
        }

        String patrolPrompt =
            "请对帮帮农农场执行一次自主巡检（触发方式：" +
            ("scheduled".equals(triggerType) ? "定时巡检" : "手动触发") + "）：\n" +
            "1. 调用 get_all_farms 获取全部农田当前传感器数据\n" +
            "2. 检查是否有需要额外干预的异常（规则引擎已处理湿度/高温/光照，请关注其他异常）\n" +
            "3. 生成一份简洁的巡检报告，格式：「整体状态：xx；异常项：xx；建议：xx」，控制在80字以内";

        try {
            // userId 非空 → resolveAiConfig 用该用户个人中心配置的 key；为空 → 降级系统 key
            AgentPlan plan = agentService.buildPlan(userId, patrolPrompt, null, "agent", "auto_patrol");
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
                return oneNetService.controlPump(true) ? "success" : "failed";
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
    public int getTotalPatrols()             { return totalPatrols.get(); }
    public int getTotalActions()             { return totalActions.get(); }
}
