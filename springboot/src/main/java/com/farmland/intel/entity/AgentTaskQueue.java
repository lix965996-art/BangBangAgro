package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Agent 自主任务队列
 */
@Data
@TableName("agent_task_queue")
public class AgentTaskQueue implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 任务唯一ID (UUID) */
    private String taskId;

    /** 关联的决策链ID */
    private String chainId;

    /** 发起用户 ID（用于按用户隔离待审批列表与去重） */
    private Integer userId;

    /** 任务类型: irrigation / led / notification / purchase / inspection */
    private String taskType;

    /** 状态: pending / approved / executing / completed / failed / rejected */
    private String taskStatus;

    /** 优先级: low / medium / high / critical */
    private String priority;

    /** 风险等级: low / medium / high */
    private String riskLevel;

    /** 是否可自动执行 */
    private Boolean autoExecute;

    /** 关联农田 */
    private String farmName;

    /** 具体动作 */
    private String actionType;

    /** 动作参数 (JSON) */
    private String actionParams;

    /** Agent 决策推理摘要 */
    private String reasoning;

    /** RAG 知识引用 (JSON) */
    private String knowledgeRefs;

    /** 置信度 0-100 */
    private BigDecimal confidenceScore;

    /** 审批人 */
    private Integer approvedBy;

    /** 审批时间 */
    private Date approvedAt;

    /** 执行时间 */
    private Date executedAt;

    /** 执行结果 */
    private String executionResult;

    /** 创建时间 */
    private Date createdAt;

    /** 过期时间 */
    private Date expiresAt;
}
