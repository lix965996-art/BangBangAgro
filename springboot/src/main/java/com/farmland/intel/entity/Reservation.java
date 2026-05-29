package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("reservation")
@Schema(description = "预约记录")
public class Reservation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "用户id")
    private Integer userId;

    @Schema(description = "农场id")
    private Integer farmId;

    @Schema(description = "到访日期")
    private LocalDate visitDate;

    @Schema(description = "到访时间")
    private String visitTime;

    @Schema(description = "车牌号")
    private String carNumber;

    @Schema(description = "预计重量(kg)")
    private BigDecimal expectedWeight;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态(pending/confirmed/cancelled/completed)")
    private String status;

    @Schema(description = "核销码")
    private String verifyCode;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
