package com.farmland.intel.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.farmland.intel.common.Result;
import com.farmland.intel.config.interceptor.AuthAccess;
import com.farmland.intel.controller.dto.AlertCreateDTO;
import com.farmland.intel.entity.FarmlandAlert;
import com.farmland.intel.entity.Notice;
import com.farmland.intel.entity.Statistic;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IFarmlandAlertService;
import com.farmland.intel.utils.TokenUtils;
import com.farmland.intel.service.INoticeService;
import com.farmland.intel.service.IStatisticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 农田预警控制器
 */
@RestController
@RequestMapping("/alert")
public class FarmlandAlertController {

    @Autowired
    private IFarmlandAlertService alertService;

    @Autowired(required = false)
    private IStatisticService statisticService;

    @Autowired(required = false)
    private INoticeService noticeService;

    /**
     * 可选：配置后，请求头须带 {@code X-Alert-Callback-Secret} 且值一致，避免公网裸奔。
     * 不配则仅依赖 {@link AuthAccess}（无需登录，适合 OneNET 等平台 HTTP 推送）。
     */
    @Value("${iot.callback-secret:}")
    private String iotCallbackSecret;

    /**
     * 获取今日任务清单（未处理的预警）
     */
    @GetMapping("/today/tasks")
    public Result getTodayTasks() {
        List<FarmlandAlert> alerts = alertService.selectTodayPendingAlerts();
        return Result.success(alerts);
    }

    /**
     * 获取告警类型统计（词云数据）
     */
    @GetMapping("/tags")
    public Result getAlertTags() {
        return Result.success(alertService.getAlertTypeStats(4));
    }

    /**
     * 获取所有未处理的预警
     */
    @GetMapping("/pending")
    public Result getPendingAlerts() {
        List<FarmlandAlert> alerts = alertService.list(
            new QueryWrapper<FarmlandAlert>()
                .eq("status", "pending")
                .orderByDesc("create_time")
        );
        return Result.success(alerts);
    }

    /**
     * 手动创建预警，供视觉巡检或人工复核写入闭环中心
     */
    @PostMapping("/manual")
    public Result createManualAlert(@RequestBody AlertCreateDTO request) {
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.error("400", "预警内容不能为空");
        }

        FarmlandAlert alert = new FarmlandAlert();
        alert.setFarmlandId(request.getFarmlandId());
        alert.setFarmlandName(
                request.getFarmlandName() == null || request.getFarmlandName().trim().isEmpty()
                        ? "视觉巡检点位"
                        : request.getFarmlandName().trim()
        );
        alert.setAlertType(
                request.getAlertType() == null || request.getAlertType().trim().isEmpty()
                        ? "visual"
                        : request.getAlertType().trim()
        );
        alert.setAlertLevel(
                request.getAlertLevel() == null || request.getAlertLevel().trim().isEmpty()
                        ? "medium"
                        : request.getAlertLevel().trim()
        );
        alert.setCurrentValue(request.getCurrentValue());
        alert.setThresholdMin(request.getThresholdMin());
        alert.setThresholdMax(request.getThresholdMax());
        alert.setMessage(request.getMessage().trim());
        alert.setSuggestion(
                request.getSuggestion() == null || request.getSuggestion().trim().isEmpty()
                        ? "建议复核现场状态，并根据需要执行设备联动或人工处置"
                        : request.getSuggestion().trim()
        );
        alert.setStatus("pending");
        alert.setCreateTime(LocalDateTime.now());
        alert.setProcessor(request.getProcessor());

        alertService.save(alert);
        return Result.success(alert);
    }

    /**
     * 标记预警为已处理
     */
    @PostMapping("/{id}/process")
    public Result processAlert(@PathVariable Integer id, @RequestBody(required = false) FarmlandAlert alert) {
        FarmlandAlert existingAlert = alertService.getById(id);
        if (existingAlert == null) {
            return Result.error("404", "预警不存在");
        }

        existingAlert.setStatus("processed");
        existingAlert.setProcessTime(LocalDateTime.now());
        if (alert != null && alert.getProcessor() != null) {
            existingAlert.setProcessor(alert.getProcessor());
        }
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null) {
            existingAlert.setProcessorUserId(currentUser.getId());
        }

        alertService.updateById(existingAlert);
        return Result.success();
    }

    /**
     * 批量处理预警
     */
    @PostMapping("/batch/process")
    public Result batchProcessAlerts(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("400", "ID列表不能为空");
        }
        User currentUser = TokenUtils.getCurrentUser();
        for (Integer id : ids) {
            FarmlandAlert alert = alertService.getById(id);
            if (alert != null && "pending".equals(alert.getStatus())) {
                alert.setStatus("processed");
                alert.setProcessTime(LocalDateTime.now());
                if (currentUser != null) {
                    alert.setProcessorUserId(currentUser.getId());
                }
                alertService.updateById(alert);
            }
        }
        return Result.success();
    }

    /**
     * 获取指定农田的预警列表
     */
    @GetMapping("/farmland/{farmlandId}")
    public Result getFarmlandAlerts(@PathVariable Integer farmlandId) {
        List<FarmlandAlert> alerts = alertService.list(
            new QueryWrapper<FarmlandAlert>()
                .eq("farmland_id", farmlandId)
                .orderByDesc("create_time")
        );
        return Result.success(alerts);
    }

    /**
     * IoT / 企业微信 / OneNET 等外部平台 HTTP 推送入口：解析视觉检测结果，写入待处理预警（及可选系统公告）。
     * 与前端 {@code POST /alert/notification/callback} 对齐；无需登录（{@link AuthAccess}），可选共享密钥见 {@code iot.callback-secret}。
     */
    @PostMapping("/notification/callback")
    @AuthAccess
    public Result visualNotificationCallback(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {
        if (StrUtil.isBlank(iotCallbackSecret)) {
            return Result.error("503", "callback endpoint not configured: set IOT_CALLBACK_SECRET");
        }
        String secret = request.getHeader("X-Alert-Callback-Secret");
        if (!iotCallbackSecret.equals(secret)) {
            return Result.error("403", "callback secret mismatch");
        }
        if (body == null || body.isEmpty()) {
            return Result.error("400", "body 不能为空");
        }
        JSONObject payload = extractVisualPayload(body);
        if (payload == null || !payload.containsKey("farm_id")) {
            return Result.error("400", "无法解析 payload：需要 farm_id，或 OneNET msg.body.value JSON");
        }

        Integer farmId = payload.getInt("farm_id");
        if (farmId == null) {
            return Result.error("400", "farm_id 无效");
        }
        String fileUrl = payload.getStr("file_url");
        String eventName = payload.getStr("event_name");
        if (StrUtil.isBlank(eventName)) {
            eventName = "IoT视觉预警";
        }
        String farmName = resolveFarmName(farmId);
        BigDecimal matureIndex = payload.getBigDecimal("mature_index");
        BigDecimal diseaseConfidence = payload.getBigDecimal("disease_confidence");
        String rawDetection = payload.getStr("raw_detection");

        StringBuilder message = new StringBuilder();
        message.append("[").append(eventName).append("] ");
        message.append("农田：").append(farmName).append("（id=").append(farmId).append("）。");
        if (StrUtil.isNotBlank(rawDetection)) {
            message.append(" 检测结果：").append(rawDetection).append("。");
        }
        if (matureIndex != null) {
            message.append(" 成熟度指数：").append(matureIndex.stripTrailingZeros().toPlainString()).append("。");
        }
        if (diseaseConfidence != null) {
            message.append(" 病害置信度：").append(diseaseConfidence.stripTrailingZeros().toPlainString()).append("。");
        }
        if (StrUtil.isNotBlank(fileUrl)) {
            message.append(" 图片：").append(fileUrl);
        }

        String level = "medium";
        if (diseaseConfidence != null && diseaseConfidence.compareTo(new BigDecimal("0.7")) >= 0) {
            level = "high";
        }

        FarmlandAlert alert = new FarmlandAlert();
        alert.setFarmlandId(farmId);
        alert.setFarmlandName(farmName);
        alert.setAlertType("iot_visual");
        alert.setAlertLevel(level);
        alert.setCurrentValue(matureIndex != null ? matureIndex : diseaseConfidence);
        alert.setMessage(message.toString());
        alert.setSuggestion(
                StrUtil.isNotBlank(fileUrl)
                        ? "建议打开「果蔬检测」或现场复核；大图链接：" + fileUrl
                        : "建议现场复核并根据需要处置。"
        );
        alert.setStatus("pending");
        alert.setCreateTime(LocalDateTime.now());
        alert.setProcessor("IoT链路");
        alertService.save(alert);

        if (noticeService != null) {
            try {
                Notice n = new Notice();
                n.setName(eventName);
                n.setContent(message.toString());
                n.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                n.setUser("IoT告警");
                noticeService.save(n);
            } catch (Exception ignored) {
                // 公告写入失败不影响预警主流程
            }
        }

        return Result.success(alert);
    }

    private String resolveFarmName(Integer farmId) {
        if (statisticService == null) {
            return "农田#" + farmId;
        }
        Statistic s = statisticService.getById(farmId);
        if (s != null && StrUtil.isNotBlank(s.getFarm())) {
            return s.getFarm();
        }
        return "农田#" + farmId;
    }

    /**
     * 支持两种形态：① 扁平 JSON（含 file_url / farm_id）；② OneNET 风格 msg.body.value 为内嵌 JSON 字符串。
     */
    @SuppressWarnings("unchecked")
    private JSONObject extractVisualPayload(Map<String, Object> root) {
        if (root.containsKey("file_url") || root.containsKey("farm_id")) {
            return JSONUtil.parseObj(root);
        }
        Object msg = root.get("msg");
        if (msg instanceof Map) {
            Map<String, Object> msgMap = (Map<String, Object>) msg;
            Object bodyObj = msgMap.get("body");
            if (bodyObj instanceof Map) {
                Map<String, Object> bodyMap = (Map<String, Object>) bodyObj;
                Object value = bodyMap.get("value");
                if (value instanceof String && StrUtil.isNotBlank((String) value)) {
                    return JSONUtil.parseObj((String) value);
                }
                if (value instanceof Map) {
                    return JSONUtil.parseObj(JSONUtil.toJsonStr(value));
                }
            }
        }
        return null;
    }
}
