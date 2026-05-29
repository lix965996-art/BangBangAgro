package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 农田预警实体类
 */
@Data
@TableName("farmland_alert")
public class FarmlandAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 农田ID
     */
    private Integer farmlandId;

    /**
     * 农田名称
     */
    private String farmlandName;

    /**
     * 预警类型：temperature/soil_humidity/air_humidity/ph/carbon/light
     */
    private String alertType;

    /**
     * 预警级别：low/medium/high
     */
    private String alertLevel;

    /**
     * 当前值
     */
    private BigDecimal currentValue;

    /**
     * 阈值下限
     */
    private BigDecimal thresholdMin;

    /**
     * 阈值上限
     */
    private BigDecimal thresholdMax;

    /**
     * 预警消息
     */
    private String message;

    /**
     * 操作建议
     */
    private String suggestion;

    /**
     * 状态：pending/processed
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 处理时间
     */
    private LocalDateTime processTime;

    /**
     * 处理人
     */
    private String processor;
}

