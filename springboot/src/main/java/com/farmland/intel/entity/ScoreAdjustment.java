package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 评分人工覆写审计 — admin 修改评分时写一行, 保留可追溯。
 */
@Data
@TableName("score_adjustment")
public class ScoreAdjustment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer userId;
    private Date windowStart;
    private Integer adminId;
    private BigDecimal oldTotal;
    private BigDecimal newTotal;
    private String reason;
    private Date createdAt;
}
