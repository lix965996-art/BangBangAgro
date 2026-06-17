package com.farmland.intel.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.farmland.intel.entity.User;
import com.farmland.intel.entity.UserScoreSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 周滚动员工评分引擎。
 *
 * 数字全部由确定性公式算出 (可审计、可申诉); LLM 只生成评语, 不参与计分。
 * 5 维: 预警响应 / 智能体作业 / 审批把关 / 出勤活跃 / 知识沉淀。
 * 每维原始子分 (0-100, 带防刷上限) → cohort 内百分位归一 → 加权求和 (N/A 维度权重重分配)。
 */
@Service
@Slf4j
public class UserScoreService {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IUserService userService;

    @Autowired
    private IUserScoreSnapshotService snapshotService;

    @Autowired(required = false)
    private AiCommentaryService aiCommentaryService;

    // ── 配置 ──
    @Value("${score.window-days:7}")                private int windowDays;
    @Value("${score.alert-sla-minutes:30}")         private int alertSlaMinutes;
    @Value("${score.ai-duration-target-ms:8000}")   private int aiDurationTargetMs;
    @Value("${score.cap.alert-count:20}")           private int capAlertCount;
    @Value("${score.cap.ai-chains:15}")             private int capAiChains;
    @Value("${score.cap.approval-count:15}")        private int capApprovalCount;
    @Value("${score.cap.knowledge-count:10}")       private int capKnowledgeCount;
    @Value("${score.min-events-for-confidence:3}")  private int minEventsForConfidence;
    @Value("${score.min-cohort-for-percentile:3}")  private int minCohortForPercentile;

    @Value("${score.weight.alert:0.30}")      private double wAlert;
    @Value("${score.weight.ai:0.25}")         private double wAi;
    @Value("${score.weight.approval:0.20}")   private double wApproval;
    @Value("${score.weight.attendance:0.15}") private double wAttendance;
    @Value("${score.weight.knowledge:0.10}")  private double wKnowledge;

    // ── 原始聚合 holder ──
    private static final class AlertStat    { int count; double avgLatency; }
    private static final class AiStat       { int chains; double avgDuration; }
    private static final class ApprovalStat { int approvedCompleted; int rejected; }
    private static final class AttendanceStat { int activeDays; }
    private static final class KnowledgeStat { int authored; int edited; }

    /**
     * 计算并落库当前窗口 (now-7d ~ now) 所有 ROLE_USER 的评分快照。返回评分用户数。
     */
    public int computeAndPersistWindow() {
        if (jdbcTemplate == null) {
            log.warn("[周评分] JdbcTemplate 不可用, 跳过");
            return 0;
        }
        Date windowEnd = new Date();
        Date windowStart = new Date(windowEnd.getTime() - windowDays * 86400_000L);

        List<User> users = userService.list(
                new QueryWrapper<User>().eq("role", "ROLE_USER"));
        if (users == null || users.isEmpty()) {
            log.info("[周评分] 无 ROLE_USER 用户, 跳过");
            return 0;
        }

        // 1. 5 维原始聚合
        Map<Integer, AlertStat> alertAgg = queryAlert(windowStart, windowEnd);
        Map<Integer, AiStat> aiAgg = queryAi(windowStart, windowEnd);
        Map<Integer, ApprovalStat> approvalAgg = queryApproval(windowStart, windowEnd);
        Map<Integer, AttendanceStat> attendanceAgg = queryAttendance(windowStart, windowEnd);
        Map<Integer, KnowledgeStat> knowledgeAgg = queryKnowledge(windowStart, windowEnd);

        // 2. 每用户原始子分 (仅记录有数据的维度)
        // dim -> (userId -> rawSub)
        Map<String, Map<Integer, Double>> rawByDim = new HashMap<>();
        rawByDim.put("alert", rawAlert(alertAgg));
        rawByDim.put("ai", rawAi(aiAgg));
        rawByDim.put("approval", rawApproval(approvalAgg));
        rawByDim.put("attendance", rawAttendance(attendanceAgg));
        rawByDim.put("knowledge", rawKnowledge(knowledgeAgg));

        // 3. cohort 百分位归一 (cohort < 3 用原始分)
        Map<String, Map<Integer, Double>> normByDim = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Double>> e : rawByDim.entrySet()) {
            normByDim.put(e.getKey(), percentileNormalize(e.getValue()));
        }

        // 4. 加权求和 + 落库
        int n = 0;
        for (User u : users) {
            try {
                persistOne(u.getId(), windowStart, windowEnd, normByDim,
                        alertAgg, aiAgg, approvalAgg, attendanceAgg, knowledgeAgg);
                n++;
            } catch (Exception ex) {
                log.warn("[周评分] 用户 {} 落库失败: {}", u.getId(), ex.getMessage());
            }
        }
        log.info("[周评分] 窗口 {} ~ {}, 评 {} 名 ROLE_USER 用户", windowStart, windowEnd, n);
        return n;
    }

    @SuppressWarnings("unchecked")
    private void persistOne(Integer userId, Date windowStart, Date windowEnd,
                            Map<String, Map<Integer, Double>> normByDim,
                            Map<Integer, AlertStat> alertAgg,
                            Map<Integer, AiStat> aiAgg,
                            Map<Integer, ApprovalStat> approvalAgg,
                            Map<Integer, AttendanceStat> attendanceAgg,
                            Map<Integer, KnowledgeStat> knowledgeAgg) {
        Double alert = normByDim.get("alert").get(userId);
        Double ai = normByDim.get("ai").get(userId);
        Double approval = normByDim.get("approval").get(userId);
        Double attendance = normByDim.get("attendance").get(userId);
        Double knowledge = normByDim.get("knowledge").get(userId);

        // 事件计数 (data_thin 判定)
        int alertCount = alertAgg.containsKey(userId) ? alertAgg.get(userId).count : 0;
        int aiChains = aiAgg.containsKey(userId) ? aiAgg.get(userId).chains : 0;
        int approvalCount = approvalAgg.containsKey(userId)
                ? (approvalAgg.get(userId).approvedCompleted + approvalAgg.get(userId).rejected) : 0;
        int knowledgeCount = knowledgeAgg.containsKey(userId)
                ? (knowledgeAgg.get(userId).authored + knowledgeAgg.get(userId).edited) : 0;
        int totalEvents = alertCount + aiChains + approvalCount + knowledgeCount
                + (attendanceAgg.containsKey(userId) ? 1 : 0);
        boolean dataThin = totalEvents < minEventsForConfidence;

        // 加权 (N/A 维度权重重分配)
        double[][] active = activeWeighted(alert, ai, approval, attendance, knowledge);
        // active: rows of {sub, weight}
        double total;
        if (active.length == 0) {
            // 全 N/A
            total = 0.0;
            dataThin = true;
        } else {
            double wsum = 0;
            for (double[] r : active) wsum += r[1];
            double acc = 0;
            for (double[] r : active) acc += (r[1] / wsum) * r[0];
            total = acc;
        }

        String grade = gradeOf(total);

        UserScoreSnapshot s = new UserScoreSnapshot();
        s.setUserId(userId);
        s.setWindowStart(windowStart);
        s.setWindowEnd(windowEnd);
        s.setAlertSub(bd(alert));
        s.setAiSub(bd(ai));
        s.setApprovalSub(bd(approval));
        s.setAttendanceSub(bd(attendance));
        s.setKnowledgeSub(bd(knowledge));
        s.setTotal(BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP));
        s.setGrade(grade);
        s.setDataThin(dataThin ? 1 : 0);
        s.setIsOverride(0);
        s.setComputedAt(new Date());
        s.setCommentary(null);

        snapshotService.save(s);

        // LLM 评语 (失败降级模板, 不影响分数)
        if (aiCommentaryService != null) {
            try {
                Map<String, Object> ctx = new LinkedHashMap<>();
                ctx.put("alertSub", alert); ctx.put("aiSub", ai);
                ctx.put("approvalSub", approval); ctx.put("attendanceSub", attendance);
                ctx.put("knowledgeSub", knowledge);
                ctx.put("total", total); ctx.put("grade", grade);
                ctx.put("alertCount", alertCount);
                ctx.put("alertLatencyMin", alertAgg.containsKey(userId) ? round1(alertAgg.get(userId).avgLatency) : null);
                ctx.put("aiChains", aiChains);
                ctx.put("aiDurMs", aiAgg.containsKey(userId) ? (int) aiAgg.get(userId).avgDuration : null);
                ctx.put("approvalCount", approvalCount);
                ctx.put("approvedCompleted", approvalAgg.containsKey(userId) ? approvalAgg.get(userId).approvedCompleted : 0);
                ctx.put("activeDays", attendanceAgg.containsKey(userId) ? attendanceAgg.get(userId).activeDays : 0);
                ctx.put("knowledgeCount", knowledgeCount);
                ctx.put("totalEvents", totalEvents);
                ctx.put("dataThin", dataThin);
                ctx.put("windowDays", windowDays);
                ctx.put("slaMinutes", alertSlaMinutes);
                String commentary = aiCommentaryService.generate(userId, windowEnd, ctx);
                if (commentary != null && !commentary.isBlank()) {
                    s.setCommentary(commentary);
                    snapshotService.updateById(s);
                }
            } catch (Exception e) {
                log.debug("[周评分] 用户 {} 评语生成失败, 跳过: {}", userId, e.getMessage());
            }
        }
    }

    /** 返回有数据维度的 [sub, weight] 数组; N/A 维度剔除。 */
    private double[][] activeWeighted(Double alert, Double ai, Double approval,
                                      Double attendance, Double knowledge) {
        List<double[]> list = new ArrayList<>();
        if (alert != null)      list.add(new double[]{alert, wAlert});
        if (ai != null)         list.add(new double[]{ai, wAi});
        if (approval != null)   list.add(new double[]{approval, wApproval});
        if (attendance != null) list.add(new double[]{attendance, wAttendance});
        if (knowledge != null)  list.add(new double[]{knowledge, wKnowledge});
        return list.toArray(new double[0][]);
    }

    // ══════════════════════════════════════════════════
    // 原始子分公式 (0-100, 带防刷上限 + 离群截断)
    // ══════════════════════════════════════════════════

    private Map<Integer, Double> rawAlert(Map<Integer, AlertStat> agg) {
        Map<Integer, Double> r = new HashMap<>();
        for (Map.Entry<Integer, AlertStat> e : agg.entrySet()) {
            AlertStat s = e.getValue();
            double processedScore = Math.min(s.count, capAlertCount) * 60.0 / capAlertCount;
            double latency = Math.min(s.avgLatency, alertSlaMinutes * 4.0); // 4×SLA 截断抗离群
            double latencyScore = clamp(alertSlaMinutes / Math.max(latency, 1.0), 0, 1) * 40.0;
            r.put(e.getKey(), clamp(processedScore + latencyScore, 0, 100));
        }
        return r;
    }

    private Map<Integer, Double> rawAi(Map<Integer, AiStat> agg) {
        Map<Integer, Double> r = new HashMap<>();
        for (Map.Entry<Integer, AiStat> e : agg.entrySet()) {
            AiStat s = e.getValue();
            double chainScore = Math.min(s.chains, capAiChains) * 70.0 / capAiChains;
            double dur = Math.min(s.avgDuration, aiDurationTargetMs * 4.0);
            double durScore = clamp(aiDurationTargetMs / Math.max(dur, 1.0), 0, 1) * 30.0;
            r.put(e.getKey(), clamp(chainScore + durScore, 0, 100));
        }
        return r;
    }

    private Map<Integer, Double> rawApproval(Map<Integer, ApprovalStat> agg) {
        Map<Integer, Double> r = new HashMap<>();
        for (Map.Entry<Integer, ApprovalStat> e : agg.entrySet()) {
            ApprovalStat s = e.getValue();
            int good = s.approvedCompleted + s.rejected; // 合理驳回也算把关正确
            r.put(e.getKey(), Math.min(good, capApprovalCount) * 100.0 / capApprovalCount);
        }
        return r;
    }

    private Map<Integer, Double> rawAttendance(Map<Integer, AttendanceStat> agg) {
        Map<Integer, Double> r = new HashMap<>();
        for (Map.Entry<Integer, AttendanceStat> e : agg.entrySet()) {
            r.put(e.getKey(), Math.min(e.getValue().activeDays, windowDays) * 100.0 / windowDays);
        }
        return r;
    }

    private Map<Integer, Double> rawKnowledge(Map<Integer, KnowledgeStat> agg) {
        Map<Integer, Double> r = new HashMap<>();
        for (Map.Entry<Integer, KnowledgeStat> e : agg.entrySet()) {
            KnowledgeStat s = e.getValue();
            double eff = s.authored + s.edited * 0.5;
            r.put(e.getKey(), Math.min(eff, capKnowledgeCount) * 100.0 / capKnowledgeCount);
        }
        return r;
    }

    // ══════════════════════════════════════════════════
    // cohort 百分位归一 (cohort < minCohortForPercentile 用原始分)
    // ══════════════════════════════════════════════════

    private Map<Integer, Double> percentileNormalize(Map<Integer, Double> raw) {
        if (raw.size() < minCohortForPercentile) {
            return raw; // 小样本直接用原始分
        }
        List<Double> values = new ArrayList<>(raw.values());
        Map<Integer, Double> out = new HashMap<>();
        for (Map.Entry<Integer, Double> e : raw.entrySet()) {
            out.put(e.getKey(), percentileRank(e.getValue(), values));
        }
        return out;
    }

    /** 经验百分位 rank: 100 * avgRank / n, 取值约 [50/n, 100-50/n], 不至于把人压到 0。 */
    private double percentileRank(double v, List<Double> cohort) {
        int n = cohort.size();
        int le = 0, lt = 0;
        for (double x : cohort) {
            if (x <= v) le++;
            if (x < v) lt++;
        }
        double rank = (lt + le) / 2.0;
        return 100.0 * rank / n;
    }

    // ══════════════════════════════════════════════════
    // JdbcTemplate 聚合查询 (GROUP BY user_id, 7 天窗口)
    // ══════════════════════════════════════════════════

    private Map<Integer, AlertStat> queryAlert(Date start, Date end) {
        String sql = "SELECT processor_user_id AS uid, COUNT(*) AS cnt, "
                + "AVG(TIMESTAMPDIFF(MINUTE, create_time, process_time)) AS avg_latency "
                + "FROM farmland_alert "
                + "WHERE status='processed' AND processor_user_id IS NOT NULL "
                + "AND process_time >= ? AND process_time < ? "
                + "GROUP BY processor_user_id";
        Map<Integer, AlertStat> r = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            AlertStat s = new AlertStat();
            s.count = rs.getInt("cnt");
            s.avgLatency = rs.getDouble("avg_latency");
            if (rs.wasNull()) s.avgLatency = alertSlaMinutes * 4.0;
            r.put(rs.getInt("uid"), s);
        }, ts(start), ts(end));
        return r;
    }

    private Map<Integer, AiStat> queryAi(Date start, Date end) {
        String sql = "SELECT user_id AS uid, COUNT(DISTINCT chain_id) AS distinct_chains, "
                + "AVG(duration_ms) AS avg_duration "
                + "FROM agent_decision_chain "
                + "WHERE step_type='tool_call' AND user_id IS NOT NULL "
                + "AND created_at >= ? AND created_at < ? "
                + "GROUP BY user_id";
        Map<Integer, AiStat> r = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            AiStat s = new AiStat();
            s.chains = rs.getInt("distinct_chains");
            s.avgDuration = rs.getDouble("avg_duration");
            if (rs.wasNull()) s.avgDuration = aiDurationTargetMs;
            r.put(rs.getInt("uid"), s);
        }, ts(start), ts(end));
        return r;
    }

    private Map<Integer, ApprovalStat> queryApproval(Date start, Date end) {
        String sql = "SELECT approved_by AS uid, "
                + "SUM(CASE WHEN task_status='completed' THEN 1 ELSE 0 END) AS ac, "
                + "SUM(CASE WHEN task_status='rejected' THEN 1 ELSE 0 END) AS rej "
                + "FROM agent_task_queue "
                + "WHERE approved_by IS NOT NULL "
                + "AND task_status IN ('completed','rejected','failed') "
                + "AND approved_at >= ? AND approved_at < ? "
                + "GROUP BY approved_by";
        Map<Integer, ApprovalStat> r = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            ApprovalStat s = new ApprovalStat();
            s.approvedCompleted = rs.getInt("ac");
            s.rejected = rs.getInt("rej");
            r.put(rs.getInt("uid"), s);
        }, ts(start), ts(end));
        return r;
    }

    private Map<Integer, AttendanceStat> queryAttendance(Date start, Date end) {
        String sql = "SELECT uid, COUNT(DISTINCT d) AS active_days FROM ("
                + " SELECT processor_user_id AS uid, DATE(process_time) AS d FROM farmland_alert"
                + "   WHERE processor_user_id IS NOT NULL AND process_time >= ? AND process_time < ?"
                + " UNION"
                + " SELECT user_id AS uid, DATE(created_at) AS d FROM agent_decision_chain"
                + "   WHERE user_id IS NOT NULL AND step_type='tool_call' AND created_at >= ? AND created_at < ?"
                + " UNION"
                + " SELECT operator_id AS uid, DATE(patrol_time) AS d FROM auto_patrol_log"
                + "   WHERE operator_id IS NOT NULL AND patrol_time >= ? AND patrol_time < ?"
                + " UNION"
                + " SELECT created_by AS uid, DATE(created_at) AS d FROM knowledge_document"
                + "   WHERE created_by IS NOT NULL AND created_at >= ? AND created_at < ?"
                + ") t WHERE uid IS NOT NULL GROUP BY uid";
        Map<Integer, AttendanceStat> r = new HashMap<>();
        Timestamp s = ts(start), e = ts(end);
        jdbcTemplate.query(sql, rs -> {
            AttendanceStat st = new AttendanceStat();
            st.activeDays = rs.getInt("active_days");
            r.put(rs.getInt("uid"), st);
        }, s, e, s, e, s, e, s, e);
        return r;
    }

    private Map<Integer, KnowledgeStat> queryKnowledge(Date start, Date end) {
        String sql = "SELECT uid, SUM(is_author) AS authored, SUM(is_editor) AS edited FROM ("
                + " SELECT created_by AS uid, 1 AS is_author, 0 AS is_editor FROM knowledge_document"
                + "   WHERE created_by IS NOT NULL AND created_at >= ? AND created_at < ?"
                + " UNION ALL"
                + " SELECT updated_by AS uid, 0 AS is_author, 1 AS is_editor FROM knowledge_document"
                + "   WHERE updated_by IS NOT NULL AND updated_at >= ? AND updated_at < ?"
                + ") t WHERE uid IS NOT NULL GROUP BY uid";
        Map<Integer, KnowledgeStat> r = new HashMap<>();
        Timestamp s = ts(start), e = ts(end);
        jdbcTemplate.query(sql, rs -> {
            KnowledgeStat st = new KnowledgeStat();
            st.authored = rs.getInt("authored");
            st.edited = rs.getInt("edited");
            r.put(rs.getInt("uid"), st);
        }, s, e, s, e);
        return r;
    }

    // ══════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════

    private String gradeOf(double total) {
        if (total >= 90) return "S";
        if (total >= 80) return "A";
        if (total >= 70) return "B";
        if (total >= 60) return "C";
        return "D";
    }

    private BigDecimal bd(Double d) {
        return d == null ? null : BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static Timestamp ts(Date d) {
        return new Timestamp(d.getTime());
    }
}
