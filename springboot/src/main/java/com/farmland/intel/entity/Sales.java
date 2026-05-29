package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import java.math.BigDecimal;
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
@Schema(description = "Sales对象")
public class Sales implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "产品")
    private String product;

    @Schema(description = "价格（元）")
    private BigDecimal price;

    @Schema(description = "采购数量")
    private Integer number;

    @Schema(description = "采购商")
    private String buyer;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "出货人")
    private String shipper;

    @Schema(description = "备注")
    private String remark;


}
