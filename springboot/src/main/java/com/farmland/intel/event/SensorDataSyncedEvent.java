package com.farmland.intel.event;

import org.springframework.context.ApplicationEvent;

/**
 * 传感器数据同步完成事件。
 *
 * <p>由 {@code ScheduledTasks.syncOneNetData()} 在每 30 秒一次的 OneNET 数据同步
 * 写库完成后发布,触发 {@code AutoPatrolService} 的事件驱动巡检
 * (无需等待 30 分钟定时巡检)。
 *
 * <p>这是项目 P1 改造的一部分: 将巡检从"拉模式"升级为"推模式",
 * 报警延迟从 30 分钟级降低到秒级。
 */
public class SensorDataSyncedEvent extends ApplicationEvent {

    /**
     * 数据来源标识。如 "onenet-old" / "onenet-new" / "onenet"。仅用于日志和调试。
     * <p>
     * 注意: 此字段不能命名为 {@code source},因为父类 {@link java.util.EventObject}
     * 已有 {@code getSource()} 方法返回发布者引用,命名冲突会导致父类语义被覆盖。
     */
    private final String sourceLabel;

    public SensorDataSyncedEvent(Object publisher, String sourceLabel) {
        super(publisher);
        this.sourceLabel = sourceLabel;
    }

    /**
     * 返回数据来源标识(自定义字段)。
     * <p>
     * 注意区分:
     * <ul>
     *   <li>{@link #getSource()} (来自父类) — 返回事件发布者对象(Object)</li>
     *   <li>{@link #getSourceLabel()} (本类新增) — 返回数据来源的字符串标签</li>
     * </ul>
     */
    public String getSourceLabel() {
        return sourceLabel;
    }
}
