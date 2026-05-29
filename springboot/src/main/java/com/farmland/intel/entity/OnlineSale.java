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

/**
 * 农作物在线销售实体
 */
@Getter
@Setter
@TableName("online_sale")
@Schema(description = "农作物在线销售表")
public class OnlineSale implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "关联库存ID")
    private Integer inventoryId;

    @Schema(description = "商品名称")
    private String produce;

    @Schema(description = "所属仓库")
    private String warehouse;

    @Schema(description = "出售数量")
    private Integer quantity;

    @Schema(description = "单价(元)")
    private BigDecimal price;

    @Schema(description = "总价(元)")
    private BigDecimal totalPrice;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "销售员")
    private String seller;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "备注")
    private String remark;
}
