package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("group_buy")
@Schema(description = "团购")
public class GroupBuy implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "农场id")
    private Integer farmId;

    @Schema(description = "发起人id")
    private Integer creatorId;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "团购价")
    private BigDecimal price;

    @Schema(description = "原价")
    private BigDecimal originalPrice;

    @Schema(description = "目标重量(kg)")
    private BigDecimal targetWeight;

    @Schema(description = "当前已凑重量(kg)")
    private BigDecimal currentWeight;

    @Schema(description = "状态(active/completed/cancelled)")
    private String status;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "截止时间")
    private LocalDateTime endTime;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
