package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author 
 * @since 2023-02-20
 */
@Getter
@Setter
@Schema(description = "Inventory对象")
public class Inventory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "产品")
    private String produce;

    @Schema(description = "仓库")
    private String warehouse;

    @Schema(description = "存储区")
    private String region;

    @Schema(description = "数量")
    private Integer number;

    @Schema(description = "安全库存")
    private Integer safeStock;

    @Schema(description = "最大库存")
    private Integer maxStock;

    @Schema(description = "日均消耗量")
    private java.math.BigDecimal dailyConsumption;

    @Schema(description = "最后出库时间")
    private java.time.LocalDateTime lastOutboundTime;

    @Schema(description = "仓库管理员")
    private String keeper;

    @Schema(description = "备注")
    private String remark;


}
