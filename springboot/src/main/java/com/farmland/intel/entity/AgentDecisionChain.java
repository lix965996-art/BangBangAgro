package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent 决策链日志：记录每一步推理过程
 */
@Data
@TableName("agent_decision_chain")
public class AgentDecisionChain implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 一次推理的唯一标识(UUID) */
    private String chainId;

    /** 触发用户, NULL=系统自主 */
    private Integer userId;

    /** 触发来源: user_chat / auto_patrol / sensor_event / scheduled */
    private String triggerSource;

    /** 用户原始问题或触发描述 */
    private String userQuestion;

    /** 步骤序号, 从0开始 */
    private Integer stepIndex;

    /** 步骤类型: thinking / tool_call / tool_result / final_answer */
    private String stepType;

    /** 步骤内容 */
    private String stepContent;

    /** 结构化详情(JSON) */
    private String stepDetail;

    /** 本轮调用的模型名称 */
    private String modelName;

    /** Function Calling 第几轮 */
    private Integer roundNumber;

    /** 本步骤耗时(毫秒) */
    private Integer durationMs;

    /** 创建时间 */
    private Date createdAt;
}
