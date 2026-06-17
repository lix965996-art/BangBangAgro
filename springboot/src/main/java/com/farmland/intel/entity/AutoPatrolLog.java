package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 无人农场自主巡检决策日志
 */
@Data
@TableName("auto_patrol_log")
public class AutoPatrolLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 巡检时间 */
    private Date patrolTime;

    /** 触发方式：scheduled / manual */
    private String triggerType;

    /** 手动触发操作员ID (sys_user.id, 周滚动评分归因用) */
    private Integer operatorId;

    /** 涉及农田名称，null 表示全局巡检记录 */
    private String farmName;

    /** 操作类型：irrigation_on / led_on / send_notification / ai_analysis / no_action */
    private String actionType;

    /** 操作详情描述 */
    private String actionDetail;

    /** 触发原因 */
    private String reason;

    /** 执行结果：success / failed / skipped / no_action */
    private String result;

    /** AI 全局巡检分析报告 */
    private String aiReport;
}
