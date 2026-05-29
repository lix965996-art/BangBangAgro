package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物资出库记录实体类
 */
@Data
@TableName("inventory_outbound")
public class InventoryOutbound implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 物资ID
     */
    private Integer inventoryId;

    /**
     * 产品名称
     */
    private String produce;

    /**
     * 出库数量
     */
    private Integer quantity;

    /**
     * 出库时间
     */
    private LocalDateTime outboundTime;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 备注
     */
    private String remark;
}

