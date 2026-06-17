package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 周滚动员工评分快照 — 每个评分窗口追加一行, 保留历史。
 */
@Data
@TableName("user_score_snapshot")
public class UserScoreSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer userId;
    private Date windowStart;
    private Date windowEnd;

    /** 5 维子分 0-100; NULL 表示该维度本周无数据(N/A) */
    private BigDecimal attendanceSub;
    private BigDecimal alertSub;
    private BigDecimal aiSub;
    private BigDecimal approvalSub;
    private BigDecimal knowledgeSub;

    /** 加权总分 0-100 */
    private BigDecimal total;

    /** 评级 S/A/B/C/D */
    private String grade;

    /** AI 综合评语 */
    private String commentary;

    /** 1=本周作业事件过少, 评级仅供参考 */
    private Integer dataThin;

    /** 1=管理员已覆写总分 */
    private Integer isOverride;

    private Date computedAt;
}
