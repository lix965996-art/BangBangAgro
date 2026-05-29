package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 传感器事件
 */
@Data
@TableName("sensor_event")
public class SensorEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 农田名称 */
    private String farmName;

    /** 事件类型: threshold_breach / anomaly / trend_change */
    private String eventType;

    /** 指标: soil_humidity / temperature / light / air_humidity */
    private String metricName;

    /** 当前值 */
    private BigDecimal currentValue;

    /** 阈值 */
    private BigDecimal thresholdValue;

    /** 严重程度: low / medium / high / critical */
    private String severity;

    /** 是否已处理 */
    private Boolean handled;

    /** 触发的决策链ID */
    private String chainId;

    /** 创建时间 */
    private Date createdAt;
}
