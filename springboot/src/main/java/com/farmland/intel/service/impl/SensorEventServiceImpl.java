package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.SensorEvent;
import com.farmland.intel.mapper.SensorEventMapper;
import com.farmland.intel.service.ISensorEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class SensorEventServiceImpl extends ServiceImpl<SensorEventMapper, SensorEvent>
        implements ISensorEventService {

    // 阈值配置
    @Value("${patrol.soil-humidity-warn:25}")
    private int soilHumidityWarn;

    @Value("${patrol.temperature-warn:38}")
    private double temperatureWarn;

    @Value("${patrol.light-warn:500}")
    private int lightWarn;

    @Value("${patrol.air-humidity-warn:30}")
    private int airHumidityWarn;

    @Override
    public SensorEvent createEvent(String farmName, String eventType, String metricName,
                                   BigDecimal currentValue, BigDecimal thresholdValue,
                                   String severity) {
        SensorEvent event = new SensorEvent();
        event.setFarmName(farmName);
        event.setEventType(eventType);
        event.setMetricName(metricName);
        event.setCurrentValue(currentValue);
        event.setThresholdValue(thresholdValue);
        event.setSeverity(severity);
        event.setHandled(false);
        event.setCreatedAt(new Date());
        save(event);
        log.info("传感器事件: farm={}, metric={}, value={}, threshold={}, severity={}",
                farmName, metricName, currentValue, thresholdValue, severity);
        return event;
    }

    @Override
    public List<SensorEvent> getUnhandledEvents(int limit) {
        return list(Wrappers.<SensorEvent>lambdaQuery()
                .eq(SensorEvent::getHandled, false)
                .orderByAsc(SensorEvent::getCreatedAt)
                .last("LIMIT " + limit));
    }

    @Override
    public boolean markHandled(Long eventId, String chainId) {
        return update(Wrappers.<SensorEvent>lambdaUpdate()
                .eq(SensorEvent::getId, eventId)
                .set(SensorEvent::getHandled, true)
                .set(SensorEvent::getChainId, chainId));
    }

    @Override
    public void markAllHandled(List<Long> eventIds, String chainId) {
        if (eventIds == null || eventIds.isEmpty()) return;
        for (Long id : eventIds) {
            markHandled(id, chainId);
        }
    }

    @Override
    public List<SensorEvent> detectEvents(String farmName, String metricName, BigDecimal currentValue) {
        List<SensorEvent> events = new ArrayList<>();
        if (currentValue == null) return events;

        double value = currentValue.doubleValue();

        switch (metricName) {
            case "soil_humidity":
                if (value < soilHumidityWarn) {
                    String severity = value < 15 ? "critical" : value < 20 ? "high" : "medium";
                    events.add(createEvent(farmName, "threshold_breach", metricName,
                            currentValue, BigDecimal.valueOf(soilHumidityWarn), severity));
                }
                break;
            case "temperature":
                if (value > temperatureWarn) {
                    String severity = value > 42 ? "critical" : value > 40 ? "high" : "medium";
                    events.add(createEvent(farmName, "threshold_breach", metricName,
                            currentValue, BigDecimal.valueOf(temperatureWarn), severity));
                }
                break;
            case "light":
                if (value < lightWarn) {
                    String severity = value < 200 ? "high" : "medium";
                    events.add(createEvent(farmName, "threshold_breach", metricName,
                            currentValue, BigDecimal.valueOf(lightWarn), severity));
                }
                break;
            case "air_humidity":
                if (value < airHumidityWarn) {
                    String severity = value < 20 ? "high" : "medium";
                    events.add(createEvent(farmName, "threshold_breach", metricName,
                            currentValue, BigDecimal.valueOf(airHumidityWarn), severity));
                }
                break;
            default:
                break;
        }
        return events;
    }

    @Override
    public List<SensorEvent> getRecentEvents(int limit) {
        return list(Wrappers.<SensorEvent>lambdaQuery()
                .orderByDesc(SensorEvent::getCreatedAt)
                .last("LIMIT " + limit));
    }
}
