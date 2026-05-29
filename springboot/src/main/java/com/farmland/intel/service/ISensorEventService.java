package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.SensorEvent;

import java.math.BigDecimal;
import java.util.List;

/**
 * 传感器事件服务
 */
public interface ISensorEventService extends IService<SensorEvent> {

    /**
     * 创建传感器事件
     */
    SensorEvent createEvent(String farmName, String eventType, String metricName,
                            BigDecimal currentValue, BigDecimal thresholdValue,
                            String severity);

    /**
     * 获取未处理的事件
     */
    List<SensorEvent> getUnhandledEvents(int limit);

    /**
     * 标记事件已处理
     */
    boolean markHandled(Long eventId, String chainId);

    /**
     * 批量标记已处理
     */
    void markAllHandled(List<Long> eventIds, String chainId);

    /**
     * 检测传感器数据是否触发事件
     * 返回新创建的事件列表
     */
    List<SensorEvent> detectEvents(String farmName, String metricName,
                                   BigDecimal currentValue);

    /**
     * 获取最近的事件
     */
    List<SensorEvent> getRecentEvents(int limit);
}
