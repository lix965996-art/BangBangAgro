package com.farmland.intel.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SensorDataSyncedEvent 单元测试 (P1 改造关键事件类)
 * <p>
 * 验证事件对象在 ScheduledTasks.publishEvent 和 AutoPatrolService.onSensorDataSynced
 * 之间能正确携带数据来源标识。
 */
class SensorDataSyncedEventTest {

    @Test
    void shouldCarryBothPublisherAndSourceLabel() {
        Object publisher = new Object();
        SensorDataSyncedEvent event = new SensorDataSyncedEvent(publisher, "onenet");

        // getSourceLabel() 是本类新增的字符串标签
        assertEquals("onenet", event.getSourceLabel(), "数据来源标签应正确传递");
        // getSource() 继承自 EventObject,返回发布者引用
        assertSame(publisher, event.getSource(), "ApplicationEvent.getSource() 应返回发布者");
    }

    @Test
    void shouldAcceptNullSourceLabel() {
        // 即使来源标签是 null 也不应抛错(用于兼容性场景)
        SensorDataSyncedEvent event = new SensorDataSyncedEvent(new Object(), null);
        assertNull(event.getSourceLabel(), "null sourceLabel 应正确返回");
    }

    @Test
    void shouldRecordTimestamp() {
        long before = System.currentTimeMillis();
        SensorDataSyncedEvent event = new SensorDataSyncedEvent(new Object(), "onenet-new");
        long after = System.currentTimeMillis();

        // 验证 ApplicationEvent 自带的时间戳在合理范围
        assertTrue(event.getTimestamp() >= before, "事件时间戳应不早于创建时刻");
        assertTrue(event.getTimestamp() <= after, "事件时间戳应不晚于创建结束");
    }
}
