package com.farmland.intel.config;

import com.farmland.intel.entity.SensorReading;
import com.farmland.intel.entity.Statistic;
import com.farmland.intel.event.SensorDataSyncedEvent;
import com.farmland.intel.mapper.SensorReadingMapper;
import com.farmland.intel.mapper.StatisticMapper;
import com.farmland.intel.service.IOneNetService;
import com.farmland.intel.service.ISensorEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 定时任务配置
 * 用于定期同步 OneNET 数据到本地数据库
 * 并实现自动灌溉功能
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    @Autowired(required = false)
    private IOneNetService oneNetService;

    @Autowired(required = false)
    private StatisticMapper statisticMapper;

    @Autowired(required = false)
    private SensorReadingMapper sensorReadingMapper;

    @Autowired(required = false)
    private ISensorEventService sensorEventService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private com.farmland.intel.service.UserScoreService userScoreService;

    private final AtomicBoolean scoring = new AtomicBoolean(false);

    @Value("${score.enabled:true}")
    private boolean scoreEnabled;

    @Value("${sensor.retention-days:90}")
    private int sensorRetentionDays;

    /**
     * 土壤湿度阈值（低于此值自动开启灌溉）
     * 可通过 application.yml 配置：auto-irrigation.soil-humidity-threshold
     */
    @Value("${auto-irrigation.soil-humidity-threshold:30}")
    private int soilHumidityThreshold;

    /**
     * 空气湿度阈值（低于此值自动开启灌溉）
     * 可通过 application.yml 配置：auto-irrigation.air-humidity-threshold
     */
    @Value("${auto-irrigation.air-humidity-threshold:40}")
    private int airHumidityThreshold;

    /**
     * 温度阈值（高于此值自动开启灌溉降温）
     * 可通过 application.yml 配置：auto-irrigation.temperature-threshold
     */
    @Value("${auto-irrigation.temperature-threshold:35}")
    private int temperatureThreshold;

    /**
     * 每 30 秒从 OneNET 同步一次传感器数据到数据库
     * 同时同步两个设备：
     * 1. 旧设备 CzL61ga8FI (STM32-001) - 温湿度 + LED
     * 2. 新设备 KK57iNOm8d (STM32-PUMP) - 温湿度 + LED + 水泵
     */
    @Scheduled(fixedRate = 30000) // 30 秒
    public void syncOneNetData() {
        if (!syncing.compareAndSet(false, true)) {
            log.debug("上一次同步尚未完成，跳过本次");
            return;
        }
        try {
        if (oneNetService != null) {
            // 同步旧设备数据 (CzL61ga8FI)
            try {
                boolean success1 = oneNetService.syncDataToDatabase();
                if (!success1) {
                    log.debug("旧设备 (CzL61ga8FI) 数据同步失败");
                } else {
                    log.debug("旧设备 (CzL61ga8FI) 数据同步成功");
                }
            } catch (Exception e) {
                log.warn("旧设备数据同步异常", e);
            }

            // 同步新设备数据 (KK57iNOm8d)
            try {
                boolean success2 = oneNetService.syncNewDeviceDataToDatabase();
                if (!success2) {
                    log.debug("新设备 (KK57iNOm8d) 数据同步失败");
                } else {
                    log.debug("新设备 (KK57iNOm8d) 数据同步成功");
                }
            } catch (Exception e) {
                log.warn("新设备数据同步异常", e);
            }

            detectSensorEvents();

            // P1 改造: 发布事件,触发 AutoPatrolService 的事件驱动巡检
            // 让规则引擎在传感器数据更新后立即响应,而不是等 30 分钟定时
            try {
                eventPublisher.publishEvent(new SensorDataSyncedEvent(this, "onenet"));
            } catch (Exception e) {
                log.debug("[事件发布] 传感器数据事件发布失败,不影响主同步: {}", e.getMessage());
            }
        } else {
            log.debug("OneNET 服务未启用，跳过数据同步");
        }
        } finally {
            syncing.set(false);
        }
    }

    /**
     * 土壤湿度恢复阈值（高于此值自动关闭灌溉，默认比开启阈值高5%）
     */
    @Value("${auto-irrigation.soil-humidity-recover:35}")
    private int soilHumidityRecover;

    /**
     * 空气湿度恢复阈值（高于此值自动关闭灌溉，默认比开启阈值高5%）
     */
    @Value("${auto-irrigation.air-humidity-recover:45}")
    private int airHumidityRecover;

    /**
     * 温度恢复阈值（低于此值自动关闭灌溉，默认比开启阈值低5℃）
     */
    @Value("${auto-irrigation.temperature-recover:30}")
    private int temperatureRecover;

    /**
     * 灌溉最大持续时间（分钟），超时强制关闭水泵，防止无限运行
     */
    @Value("${auto-irrigation.max-duration-minutes:30}")
    private int maxIrrigationDurationMinutes;

    /**
     * 水泵开启的时间戳（毫秒），用于超时检测
     */
    private volatile long pumpOnTimestamp = 0;

    /**
     * 每 5 分钟检查一次自动灌溉状态
     * 触发灌溉条件（满足任一即可）：
     * 1. 土壤湿度低于阈值（默认 30%）
     * 2. 空气湿度低于阈值（默认 40%）
     * 3. 温度高于阈值（默认 35℃）
     * 关闭灌溉条件（满足所有恢复阈值，或超时）：
     * 1. 土壤湿度恢复到恢复阈值以上（默认 35%）
     * 2. 空气湿度恢复到恢复阈值以上（默认 45%）
     * 3. 温度恢复到恢复阈值以下（默认 30℃）
     * 4. 灌溉超过最大持续时间（默认 30 分钟）
     */
    @Scheduled(fixedRate = 300000) // 5 分钟
    public void autoIrrigationCheck() {
        if (oneNetService == null || statisticMapper == null) {
            log.debug("自动灌溉功能未启用");
            return;
        }

        try {
            boolean currentPumpState = getCurrentPumpState();

            if (currentPumpState) {
                // 水泵已在运行，检查是否需要关闭
                checkAndStopIrrigation();
            } else {
                // 水泵未运行，检查是否需要开启
                checkAndStartIrrigation();
            }

        } catch (Exception e) {
            log.error("[自动灌溉] 检查失败", e);
        }
    }

    /**
     * 检查并开启灌溉
     */
    private void checkAndStartIrrigation() {
        List<Statistic> farms = statisticMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Statistic>()
                .last("LIMIT 200")
        );
        if (farms == null || farms.isEmpty()) {
            log.debug("没有农田数据，跳过自动灌溉检查");
            return;
        }

        for (Statistic farm : farms) {
            boolean needIrrigation = false;
            String reason = "";

            if (farm.getSoilhumidity() != null && farm.getSoilhumidity() < soilHumidityThreshold) {
                needIrrigation = true;
                reason = String.format("土壤湿度过低（当前：%d%%，阈值：%d%%）",
                    farm.getSoilhumidity(), soilHumidityThreshold);
            }

            if (!needIrrigation && farm.getAirhumidity() != null && farm.getAirhumidity() < airHumidityThreshold) {
                needIrrigation = true;
                reason = String.format("空气湿度过低（当前：%d%%，阈值：%d%%）",
                    farm.getAirhumidity(), airHumidityThreshold);
            }

            if (!needIrrigation && farm.getTemperature() != null &&
                farm.getTemperature().doubleValue() > temperatureThreshold) {
                needIrrigation = true;
                reason = String.format("温度过高（当前：%.1f℃，阈值：%d℃）",
                    farm.getTemperature().doubleValue(), temperatureThreshold);
            }

            if (needIrrigation) {
                log.info("[自动灌溉触发] 农田 [{}] 需要灌溉：{}", farm.getFarm(), reason);
                boolean success = oneNetService.controlPump(true);
                if (success) {
                    pumpOnTimestamp = Instant.now().toEpochMilli();
                    log.info("[自动灌溉] 已开启水泵，为农田 [{}] 进行灌溉", farm.getFarm());
                } else {
                    log.error("[自动灌溉] 开启水泵失败，农田：{}", farm.getFarm());
                }
                break;
            }
        }
    }

    /**
     * 检查并关闭灌溉
     */
    private void checkAndStopIrrigation() {
        // 1. 检查超时
        long now = Instant.now().toEpochMilli();
        long elapsedMinutes = (now - pumpOnTimestamp) / 60000;
        if (elapsedMinutes >= maxIrrigationDurationMinutes) {
            log.warn("[自动灌溉超时] 水泵已运行 {} 分钟，强制关闭", elapsedMinutes);
            stopPump("达到最大运行时长");
            return;
        }

        // 2. 检查所有农田是否都已恢复
        List<Statistic> farms = statisticMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Statistic>()
                .last("LIMIT 200")
        );
        if (farms == null || farms.isEmpty()) {
            log.info("[自动灌溉] 没有农田数据，关闭水泵");
            stopPump("无农田数据");
            return;
        }

        boolean allRecovered = true;
        String pendingReason = "";
        for (Statistic farm : farms) {
            boolean needIrrigation = false;

            if (farm.getSoilhumidity() != null && farm.getSoilhumidity() < soilHumidityRecover) {
                needIrrigation = true;
                pendingReason = String.format("农田 [%s] 土壤湿度仍偏低（当前：%d%%，恢复阈值：%d%%）",
                    farm.getFarm(), farm.getSoilhumidity(), soilHumidityRecover);
            }

            if (!needIrrigation && farm.getAirhumidity() != null && farm.getAirhumidity() < airHumidityRecover) {
                needIrrigation = true;
                pendingReason = String.format("农田 [%s] 空气湿度仍偏低（当前：%d%%，恢复阈值：%d%%）",
                    farm.getFarm(), farm.getAirhumidity(), airHumidityRecover);
            }

            if (!needIrrigation && farm.getTemperature() != null &&
                farm.getTemperature().doubleValue() > temperatureRecover) {
                needIrrigation = true;
                pendingReason = String.format("农田 [%s] 温度仍偏高（当前：%.1f℃，恢复阈值：%d℃）",
                    farm.getFarm(), farm.getTemperature().doubleValue(), temperatureRecover);
            }

            if (needIrrigation) {
                allRecovered = false;
                log.debug("[自动灌溉] {}", pendingReason);
            }
        }

        if (allRecovered) {
            log.info("[自动灌溉] 所有农田指标已恢复，关闭水泵");
            stopPump("所有农田指标已恢复");
        }
    }

    /**
     * 关闭水泵
     */
    private void stopPump(String reason) {
        boolean success = oneNetService.controlPump(false);
        if (success) {
            pumpOnTimestamp = 0;
            log.info("[自动灌溉] 已关闭水泵，原因：{}", reason);
        } else {
            log.error("[自动灌溉] 关闭水泵失败，原因：{}", reason);
        }
    }

    /**
     * 获取当前水泵状态
     * @return true=水泵开启，false=水泵关闭
     */
    private boolean getCurrentPumpState() {
        try {
            if (oneNetService != null) {
                Map<String, Object> pumpData = oneNetService.getNewDeviceData();
                if ((Boolean) pumpData.getOrDefault("success", false)) {
                    Object pumpState = pumpData.get("pump");
                    if (pumpState instanceof Boolean) {
                        return (Boolean) pumpState;
                    } else if (pumpState instanceof Integer) {
                        return ((Integer) pumpState) == 1;
                    } else if (pumpState instanceof String) {
                        return "true".equalsIgnoreCase((String) pumpState) || "1".equals(pumpState);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("获取水泵状态失败：{}", e.getMessage());
        }
        return false;
    }

    /**
     * 检测传感器阈值异常，记录到 sensor_event 表。
     */
    private void detectSensorEvents() {
        if (sensorEventService == null || statisticMapper == null) {
            return;
        }
        try {
            List<Statistic> farms = statisticMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Statistic>()
                            .last("LIMIT 200")
            );
            if (farms == null || farms.isEmpty()) {
                return;
            }
            for (Statistic farm : farms) {
                String farmName = farm.getFarm() != null ? farm.getFarm() : "未命名农田";
                if (farm.getSoilhumidity() != null) {
                    sensorEventService.detectEvents(farmName, "soil_humidity",
                            java.math.BigDecimal.valueOf(farm.getSoilhumidity()));
                }
                if (farm.getTemperature() != null) {
                    sensorEventService.detectEvents(farmName, "temperature",
                            farm.getTemperature());
                }
                if (farm.getLight() != null) {
                    sensorEventService.detectEvents(farmName, "light",
                            java.math.BigDecimal.valueOf(farm.getLight()));
                }
                if (farm.getAirhumidity() != null) {
                    sensorEventService.detectEvents(farmName, "air_humidity",
                            java.math.BigDecimal.valueOf(farm.getAirhumidity()));
                }
            }
        } catch (Exception e) {
            log.debug("[传感器事件] 检测异常: {}", e.getMessage());
        }
    }

    /**
     * 每天凌晨 3 点清理超过保留期限的传感器数据
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldSensorData() {
        if (sensorReadingMapper == null) {
            log.debug("传感器数据清理未启用");
            return;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(sensorRetentionDays);
            int deleted = sensorReadingMapper.deleteByTimeBefore(cutoff);
            if (deleted > 0) {
                log.info("[数据清理] 已删除 {} 条 {} 天前的传感器数据", deleted, sensorRetentionDays);
            }
        } catch (Exception e) {
            log.error("[数据清理] 清理传感器数据失败", e);
        }
    }

    /**
     * 每天 02:30 重算周滚动员工评分 (trailing-7-day 窗口, 每天产一行快照)。
     * 仅评 ROLE_USER; 数字确定性算出, LLM 只补评语。scheduling.enabled=false 时整体不生效。
     */
    @Scheduled(cron = "${score.cron:0 30 2 * * ?}")
    public void computeWeeklyScore() {
        if (!scoreEnabled || userScoreService == null) {
            log.debug("[周评分] 未启用或服务缺失, 跳过");
            return;
        }
        if (!scoring.compareAndSet(false, true)) {
            log.warn("[周评分] 上一次尚未完成, 跳过本次");
            return;
        }
        try {
            int n = userScoreService.computeAndPersistWindow();
            log.info("[周评分] 完成, 评分 {} 名用户", n);
        } catch (Exception e) {
            log.error("[周评分] 计算失败", e);
        } finally {
            scoring.set(false);
        }
    }
}
