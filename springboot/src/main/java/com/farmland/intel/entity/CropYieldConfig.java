package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作物产量配置实体类
 */
@Data
@TableName("crop_yield_config")
public class CropYieldConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 作物名称
     */
    private String cropName;

    /**
     * 每亩产量（公斤）
     */
    private BigDecimal yieldPerMu;

    /**
     * 销售单价（元/公斤）
     */
    private BigDecimal unitPrice;

    /**
     * 每亩成本（元）
     */
    private BigDecimal costPerMu;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

