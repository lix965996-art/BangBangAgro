package com.farmland.intel.service.impl;

import com.farmland.intel.entity.AiConfig;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IAiConfigService;
import com.farmland.intel.service.IVisionAnalysisService;
import com.farmland.intel.utils.TokenUtils;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多模态视觉分析实现（OpenAI 兼容，可配豆包/火山方舟、Qwen-VL、硅基流动等）。
 * <p>
 * 走 OpenAI 兼容的 /chat/completions 接口，以 image_url(base64 data URI) 形式传图，
 * 要求模型返回严格 JSON，再映射成与 YOLO「双检」一致的结构，前端无需大改即可复用。
 * 复用 Hutool HttpRequest（与 {@link QwenServiceImpl} 同风格）以拿到更长的读超时。
 */
@Service
@Slf4j
public class VisionAnalysisServiceImpl implements IVisionAnalysisService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${vision.enabled:true}")
    private boolean enabled;

    @Value("${vision.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String baseUrl;

    @Value("${vision.api-key:}")
    private String apiKey;

    @Value("${vision.model:}")
    private String model;

    @Value("${vision.temperature:0.3}")
    private double temperature;

    @Value("${vision.timeout-ms:60000}")
    private int timeoutMs;

    @Autowired
    private IAiConfigService aiConfigService;

    /** 解析后的有效视觉配置：优先当前用户 per-user vision.*，留空回退后端 yml。 */
    private static final class VisionCfg {
        final String baseUrl;
        final String apiKey;
        final String model;
        VisionCfg(String baseUrl, String apiKey, String model) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
        }
        boolean ready() {
            return apiKey != null && !apiKey.trim().isEmpty()
                    && model != null && !model.trim().isEmpty();
        }
    }

    /** 取当前登录用户的视觉配置；任一字段为空则回退到 yml 默认值。 */
    private VisionCfg resolve() {
        String b = baseUrl, k = apiKey, m = model;
        try {
            User user = TokenUtils.getCurrentUser();
            if (user != null && user.getId() != null) {
                AiConfig cfg = aiConfigService.getByUserId(user.getId());
                if (cfg != null) {
                    if (notBlank(cfg.getVisionApiKey())) k = cfg.getVisionApiKey();
                    if (notBlank(cfg.getVisionBaseUrl())) b = cfg.getVisionBaseUrl();
                    if (notBlank(cfg.getVisionModelName())) m = cfg.getVisionModelName();
                }
            }
        } catch (Exception ignore) {
            // 无登录上下文或读取失败时静默回退 yml
        }
        return new VisionCfg(b, k, m);
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static final String SYSTEM_PROMPT =
            "你是一位资深植保与果蔬采后专家，擅长通过田间/大棚图片判读作物状态。" +
            "请只输出一个 JSON 对象，不要任何额外文字、不要 Markdown 代码块。JSON 结构如下：\n" +
            "{\n" +
            "  \"crop\": \"识别到的作物中文名（如 番茄/草莓/玉米/水稻/未知）\",\n" +
            "  \"ripeness\": {\n" +
            "    \"total\": 视野内可判定的果实总数(整数,无果实填0),\n" +
            "    \"riped\": 已成熟数(整数),\n" +
            "    \"unriped\": 未成熟数(整数),\n" +
            "    \"ratio\": 成熟百分比(0-100的数字),\n" +
            "    \"items\": [ {\"label\": \"成熟\"或\"未成熟\", \"confidence\": 0-100} ]\n" +
            "  },\n" +
            "  \"diseases\": [ {\"name\": \"病虫害中文名（English）\", \"confidence\": 0-100, \"severity\": \"轻\"或\"中\"或\"重\"} ],\n" +
            "  \"dimensions\": {\n" +
            "    \"growth_stage\": \"生长阶段（苗期/花期/结果期/成熟采收期/未知）\",\n" +
            "    \"nutrition\": \"营养状况（如 正常 / 疑似缺氮 / 缺钾 等，并简述叶片依据）\",\n" +
            "    \"pest\": \"虫害情况（无 / 具体虫害名）\",\n" +
            "    \"quality\": \"果实品质与商品性分级（如 优/中/次 + 简短理由；无果实填 不适用）\",\n" +
            "    \"harvest\": \"采收期预估（如 已可采 / 约X天后 / 不适用）\"\n" +
            "  },\n" +
            "  \"healthy\": 未见明显病虫害时为 true 否则 false,\n" +
            "  \"summary\": \"3-5句多维度综合分析：覆盖作物种类、长势、色泽/成熟阶段、叶片与果实可见异常、潜在风险\",\n" +
            "  \"advice\": \"可执行的农事处置建议，按要点书写\"\n" +
            "}\n" +
            "要求：未见果实则 ripeness 计数填 0；未见病虫害则 diseases 为空数组且 healthy=true；" +
            "dimensions 各字段无法判断时填\"未知\"或\"不适用\"，但不要省略字段；" +
            "数字字段必须是数字而非字符串；只依据图像可见信息，不臆造。";

    private static final String ASK_SYSTEM_PROMPT =
            "你是一位经验丰富的农业与植保专家，正在帮农户看田间/大棚照片。" +
            "请用简洁、专业、口语化的中文，直接回答用户关于这张图片的问题，必要时给出可操作的处置建议。" +
            "只依据图像可见信息作答，信息不足就如实说明，不要编造具体数值或品种。";

    @Override
    public boolean isConfigured() {
        return enabled && resolve().ready();
    }

    @Override
    public String modelName() {
        VisionCfg cfg = resolve();
        return (enabled && cfg.ready()) ? cfg.model : "";
    }

    @Override
    public Map<String, Object> analyze(MultipartFile file, String cropTypeHint) throws Exception {
        VisionCfg cfg = resolve();
        if (!enabled || !cfg.ready()) {
            throw new IllegalStateException("视觉模型未配置，请在「个人中心 → AI 模型配置 → 视觉模型」填写，或在后端 application-local.yml 设置 vision.*");
        }

        String hint = (cropTypeHint == null || cropTypeHint.trim().isEmpty())
                ? "用户未指定作物类型，请你自行判断作物种类（不限于番茄/玉米/水稻/草莓）。"
                : "用户标注的作物类型为「" + cropTypeHint.trim() + "」，若与实际不符请以图像为准。";
        String userText = hint + " 请综合分析这张作物图片的成熟度、病虫害与多维状况，并严格按系统要求的 JSON 输出。";

        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
        messages.add(buildImageUserMessage(userText, toDataUrl(file)));

        String content = chat(messages, cfg);
        return buildResult(content, cropTypeHint);
    }

    @Override
    public String ask(MultipartFile file, String question, String history) throws Exception {
        VisionCfg cfg = resolve();
        if (!enabled || !cfg.ready()) {
            throw new IllegalStateException("视觉模型未配置，请在「个人中心 → AI 模型配置 → 视觉模型」填写，或在后端 application-local.yml 设置 vision.*");
        }
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入问题");
        }

        StringBuilder userText = new StringBuilder();
        if (history != null && !history.trim().isEmpty()) {
            userText.append("以下是我们之前关于这张图片的问答记录，供你理解上下文：\n")
                    .append(history.trim()).append("\n\n");
        }
        userText.append("请基于这张作物图片回答：").append(question.trim());

        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().put("role", "system").put("content", ASK_SYSTEM_PROMPT));
        messages.add(buildImageUserMessage(userText.toString(), toDataUrl(file)));

        String content = chat(messages, cfg);
        return content == null ? "" : content.trim();
    }

    /** 图片转 base64 data URI。 */
    private String toDataUrl(MultipartFile file) throws Exception {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            contentType = "image/jpeg";
        }
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
        return "data:" + contentType + ";base64," + base64;
    }

    /** 构建一条「文本 + 图片」的 user 消息（OpenAI 多模态格式）。 */
    private JSONObject buildImageUserMessage(String text, String dataUrl) {
        JSONArray userContent = new JSONArray();
        userContent.add(new JSONObject().put("type", "text").put("text", text));
        userContent.add(new JSONObject().put("type", "image_url")
                .put("image_url", new JSONObject().put("url", dataUrl)));
        return new JSONObject().put("role", "user").put("content", userContent);
    }

    /** 发送一次 OpenAI 兼容 chat/completions 请求，返回首条回复文本。 */
    private String chat(JSONArray messages, VisionCfg cfg) {
        JSONObject payload = new JSONObject();
        payload.put("model", cfg.model);
        payload.put("temperature", temperature);
        payload.put("messages", messages);

        String url = normalizeBaseUrl(cfg.baseUrl) + "/chat/completions";
        String respBody = HttpRequest.post(url)
                .header("Authorization", "Bearer " + cfg.apiKey)
                .header("Content-Type", "application/json")
                .body(payload.toString())
                .timeout(timeoutMs)
                .execute()
                .body();

        JSONObject resp = JSONUtil.parseObj(respBody);
        if (resp.containsKey("error")) {
            Object err = resp.get("error");
            String msg = err instanceof JSONObject ? ((JSONObject) err).getStr("message", "未知错误") : String.valueOf(err);
            log.warn("多模态视觉返回错误: {}", msg);
            throw new RuntimeException("多模态视觉调用失败: " + msg);
        }
        JSONArray choices = resp.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("多模态视觉未返回有效结果");
        }
        return choices.getJSONObject(0).getJSONObject("message").getStr("content", "");
    }

    private String normalizeBaseUrl(String raw) {
        String b = raw == null ? "https://ark.cn-beijing.volces.com/api/v3" : raw.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }

    /** 把模型输出映射为与 YOLO /detect/both 一致的结构，额外带 ai_summary / ai_advice。 */
    private Map<String, Object> buildResult(String content, String cropTypeHint) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engine", "vision");
        data.put("detect_time", LocalDateTime.now().format(TIME_FMT));
        data.put("result_image", ""); // 多模态不回标注图，前端会回退展示原图

        JSONObject parsed = tryParseJson(content);
        if (parsed == null) {
            // 解析失败：把模型原文当作综合分析，结构置空，避免前端报错
            log.warn("多模态视觉输出非 JSON，降级为纯文本分析。原文前 200 字: {}",
                    content == null ? "" : content.substring(0, Math.min(200, content.length())));
            data.put("crop_type", cropTypeHint == null ? "" : cropTypeHint);
            data.put("ripeness_analysis", emptyRipeness());
            data.put("disease_analysis", emptyDisease());
            data.put("dimensions", new LinkedHashMap<>());
            data.put("ai_summary", content == null ? "" : content.trim());
            data.put("ai_advice", "");
            return data;
        }

        // 作物
        String crop = parsed.getStr("crop", cropTypeHint == null ? "" : cropTypeHint);
        data.put("crop_type", crop);

        // 成熟度
        data.put("ripeness_analysis", mapRipeness(parsed.getJSONObject("ripeness")));

        // 病虫害
        data.put("disease_analysis", mapDisease(parsed.getJSONArray("diseases")));

        // 多维判读
        data.put("dimensions", mapDimensions(parsed.getJSONObject("dimensions")));

        data.put("ai_summary", parsed.getStr("summary", ""));
        data.put("ai_advice", parsed.getStr("advice", ""));
        return data;
    }

    /** 多维判读字段（只保留非空项）。 */
    private Map<String, Object> mapDimensions(JSONObject dim) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (dim == null) return m;
        putIfText(m, "growth_stage", dim.getStr("growth_stage", ""));
        putIfText(m, "nutrition", dim.getStr("nutrition", ""));
        putIfText(m, "pest", dim.getStr("pest", ""));
        putIfText(m, "quality", dim.getStr("quality", ""));
        putIfText(m, "harvest", dim.getStr("harvest", ""));
        return m;
    }

    private void putIfText(Map<String, Object> m, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            m.put(key, value.trim());
        }
    }

    private Map<String, Object> mapRipeness(JSONObject ripeness) {
        List<Map<String, Object>> detections = new ArrayList<>();
        int total = 0, riped = 0, unriped = 0;
        double ratio = 0;

        if (ripeness != null) {
            total = ripeness.getInt("total", 0);
            riped = ripeness.getInt("riped", 0);
            unriped = ripeness.getInt("unriped", 0);
            ratio = ripeness.getDouble("ratio", total > 0 ? riped * 100.0 / total : 0.0);

            JSONArray items = ripeness.getJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    String label = it.getStr("label", "未成熟");
                    boolean isRipe = label.contains("成熟") && !label.contains("未");
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("class_id", isRipe ? 0 : 1);
                    d.put("class_name", isRipe ? "Riped" : "UnRiped");
                    d.put("class_name_ch", isRipe ? "成熟" : "未成熟");
                    d.put("confidence", round2(it.getDouble("confidence", 0.0)));
                    detections.add(d);
                }
            }
        }
        // 计数兜底：模型只给了 items 没给 total 时回填
        if (total == 0 && !detections.isEmpty()) {
            total = detections.size();
            riped = 0;
            for (Map<String, Object> d : detections) {
                if (Integer.valueOf(0).equals(d.get("class_id"))) riped++;
            }
            unriped = total - riped;
            ratio = total > 0 ? riped * 100.0 / total : 0;
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("riped_count", riped);
        stats.put("unriped_count", unriped);
        stats.put("riped_ratio", round2(ratio));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detections", detections);
        result.put("statistics", stats);
        return result;
    }

    private Map<String, Object> mapDisease(JSONArray diseases) {
        List<Map<String, Object>> detections = new ArrayList<>();
        if (diseases != null) {
            for (int i = 0; i < diseases.size(); i++) {
                JSONObject d = diseases.getJSONObject(i);
                String name = d.getStr("name", "").trim();
                if (name.isEmpty()) continue;
                Map<String, Object> det = new LinkedHashMap<>();
                det.put("class_name", name);
                det.put("confidence", round2(d.getDouble("confidence", 0.0)));
                det.put("severity", d.getStr("severity", ""));
                detections.add(det);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_detections", detections.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detections", detections);
        result.put("statistics", stats);
        return result;
    }

    private Map<String, Object> emptyRipeness() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", 0);
        stats.put("riped_count", 0);
        stats.put("unriped_count", 0);
        stats.put("riped_ratio", 0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detections", new ArrayList<>());
        result.put("statistics", stats);
        return result;
    }

    private Map<String, Object> emptyDisease() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_detections", 0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detections", new ArrayList<>());
        result.put("statistics", stats);
        return result;
    }

    /** 从模型输出中尽力抽取 JSON 对象（容忍 ```json 代码块与前后缀文字）。 */
    private JSONObject tryParseJson(String content) {
        if (content == null || content.trim().isEmpty()) return null;
        String s = content.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl >= 0) s = s.substring(nl + 1);
            int fence = s.lastIndexOf("```");
            if (fence >= 0) s = s.substring(0, fence);
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        String json = s.substring(start, end + 1);
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            return null;
        }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
