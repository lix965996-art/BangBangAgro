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
@TableName("group_buy_order")
@Schema(description = "团购参与记录")
public class GroupBuyOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "团购id")
    private Integer groupBuyId;

    @Schema(description = "用户id")
    private Integer userId;

    @Schema(description = "参与重量(kg)")
    private BigDecimal weight;

    @Schema(description = "金额")
    private BigDecimal amount;

    @Schema(description = "状态(paid/cancelled)")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
