package com.farmland.intel.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.farmland.intel.entity.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 评分评语生成。LLM 只产 60-100 字中文评语 + 一个 data_too_thin 提示;
 * 不参与计分 (分数由 UserScoreService 确定性算出)。网关不可用时降级模板。
 */
@Service
@Slf4j
public class AiCommentaryService {

    @Autowired(required = false)
    private ChatModelFactory chatModelFactory;

    @Value("${qwen.api-key:}")        private String apiKey;
    @Value("${qwen.api-url:https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation}")
    private String apiUrl;
    @Value("${qwen.model:qwen-max}")  private String model;
    @Value("${score.commentary.enabled:true}") private boolean enabled;

    /** 内存缓存: key = userId|yyyy-MM-dd (windowEnd) */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT =
            "你是农业平台的员工绩效评语助手。你会收到一名员工过去 7 天的 5 个维度子分(0-100, null=本周无数据)和事件计数。\n"
                    + "请输出一段 60-100 字的中文评语,聚焦该员工的优势维度和可改进维度,语气客观、具体、有建设性。\n"
                    + "严禁编造未提供的具体数字、姓名、农田名称。严禁泛泛而谈\"表现优异\"。\n"
                    + "若总事件计数过低(数据不足),在评语开头标注\"数据样本偏少,仅供参考\"。\n"
                    + "只返回严格 JSON: {\"commentary\":\"...\",\"data_too_thin\":true/false},不要任何额外文字或代码块标记。";

    @SuppressWarnings("unchecked")
    public String generate(Integer userId, Date windowEnd, Map<String, Object> ctx) {
        if (!enabled || chatModelFactory == null || StrUtil.isBlank(apiKey)) {
            return fallback(ctx);
        }
        String cacheKey = userId + "|" + new SimpleDateFormat("yyyy-MM-dd").format(windowEnd);
        String cached = cache.get(cacheKey);
        if (cached != null) return cached;

        try {
            ChatClient client = chatModelFactory.getChatClient(buildConfig());
            List<Message> msgs = new ArrayList<>();
            msgs.add(new SystemMessage(SYSTEM_PROMPT));
            msgs.add(new UserMessage(buildUserPrompt(ctx)));
            String content = client.prompt().messages(msgs).call().content();
            String commentary = parseCommentary(content);
            if (StrUtil.isBlank(commentary)) {
                commentary = fallback(ctx);
            }
            cache.put(cacheKey, commentary);
            return commentary;
        } catch (Exception e) {
            log.warn("[评分评语] LLM 调用失败, 降级模板: {}", e.getMessage());
            return fallback(ctx);
        }
    }

    /** AiConfig 兜底构建 (定时任务无 HTTP 用户, 直接用 yml 配置)。baseUrl 去掉尾部 /generation 路径。 */
    private AiConfig buildConfig() {
        AiConfig cfg = new AiConfig();
        cfg.setProvider("qwen");
        String base = apiUrl == null ? "" : apiUrl
                .replaceAll("/api/v1/services/aigc/text-generation/generation$", "")
                .replaceAll("/+$", "");
        cfg.setBaseUrl(base.isBlank() ? "https://dashscope.aliyuncs.com/compatible-mode/v1" : base);
        cfg.setApiKey(apiKey);
        cfg.setModelName(model);
        cfg.setTemperature(new BigDecimal("0.4"));
        return cfg;
    }

    private String buildUserPrompt(Map<String, Object> ctx) {
        Map<String, Object> c = ctx == null ? new LinkedHashMap<>() : ctx;
        StringBuilder sb = new StringBuilder();
        sb.append("维度子分(0-100, null=该维度本周无数据):\n");
        sb.append("- 预警响应: ").append(c.get("alertSub"))
                .append(" (处理 ").append(c.get("alertCount")).append(" 条, 平均响应 ")
                .append(c.get("alertLatencyMin")).append(" 分钟, SLA ").append(c.get("slaMinutes")).append(" 分钟)\n");
        sb.append("- 智能体作业: ").append(c.get("aiSub"))
                .append(" (调用 ").append(c.get("aiChains")).append(" 次工具链, 平均耗时 ")
                .append(c.get("aiDurMs")).append(" 毫秒)\n");
        sb.append("- 审批把关: ").append(c.get("approvalSub"))
                .append(" (审批 ").append(c.get("approvalCount")).append(" 个任务, 其中正确完成 ")
                .append(c.get("approvedCompleted")).append(" 个)\n");
        sb.append("- 出勤活跃: ").append(c.get("attendanceSub"))
                .append(" (本周活跃 ").append(c.get("activeDays")).append(" 天 / 共 ").append(c.get("windowDays")).append(" 天)\n");
        sb.append("- 知识沉淀: ").append(c.get("knowledgeSub"))
                .append(" (撰写/编辑 ").append(c.get("knowledgeCount")).append(" 篇文档)\n");
        sb.append("加权总分: ").append(c.get("total")).append(", 评级: ").append(c.get("grade")).append("\n");
        sb.append("窗口内总事件计数: ").append(c.get("totalEvents"))
                .append(" (低于 ").append(c.get("minEventsForConfidence") == null ? 3 : c.get("minEventsForConfidence"))
                .append(" 视为数据不足)\n");
        sb.append("确定性判定 data_thin=").append(c.get("dataThin")).append(" (此为权威值, 仅供你参考)\n");
        sb.append("请输出 JSON。");
        return sb.toString();
    }

    /** 从 LLM 返回中提取 commentary; 容忍前后多余文字与代码块标记。 */
    private String parseCommentary(String content) {
        if (StrUtil.isBlank(content)) return null;
        String trimmed = content.trim();
        // 去掉可能的 ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```\\w*", "").replaceAll("```$", "").trim();
        }
        try {
            JSONObject obj = JSONUtil.parseObj(trimmed);
            return obj.getStr("commentary");
        } catch (Exception e) {
            // 不是 JSON 就当纯文本评语返回 (裁剪到合理长度)
            return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
        }
    }

    private String fallback(Map<String, Object> ctx) {
        Map<String, Object> c = ctx == null ? new LinkedHashMap<>() : ctx;
        String strongest = strongestDim(c);
        boolean dataThin = Boolean.TRUE.equals(c.get("dataThin"));
        StringBuilder sb = new StringBuilder();
        if (dataThin) sb.append("数据样本偏少,仅供参考。");
        if (strongest != null) {
            sb.append("本周").append(strongest).append("维度表现较为突出。");
        } else {
            sb.append("本周各维度数据不足,暂无突出项。");
        }
        sb.append(dataThin ? "数据积累后评级会更准确。" : "建议持续保持,并关注相对薄弱的环节。");
        return sb.toString();
    }

    private String strongestDim(Map<String, Object> c) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("alertSub", "预警响应");
        names.put("aiSub", "智能体作业");
        names.put("approvalSub", "审批把关");
        names.put("attendanceSub", "出勤活跃");
        names.put("knowledgeSub", "知识沉淀");
        String bestName = null;
        double best = -1;
        for (Map.Entry<String, String> e : names.entrySet()) {
            Object v = c.get(e.getKey());
            if (v instanceof Number) {
                double d = ((Number) v).doubleValue();
                if (d > best) { best = d; bestName = e.getValue(); }
            }
        }
        return bestName;
    }
}
