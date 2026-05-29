package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 健康指数配置实体类
 */
@Data
@TableName("health_index_config")
public class HealthIndexConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 指标名称：temperature/air_humidity/soil_humidity/carbon/ph/light
     */
    private String indicator;

    /**
     * 优秀区间最小值
     */
    private BigDecimal excellentMin;

    /**
     * 优秀区间最大值
     */
    private BigDecimal excellentMax;

    /**
     * 良好区间最小值
     */
    private BigDecimal goodMin;

    /**
     * 良好区间最大值
     */
    private BigDecimal goodMax;

    /**
     * 权重（0-1）
     */
    private BigDecimal weight;

    /**
     * 预警下限
     */
    private BigDecimal thresholdMin;

    /**
     * 预警上限
     */
    private BigDecimal thresholdMax;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

