package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 禾序 Agent 按用户持久化的记忆片段（偏好 + 对话滚动摘要）。
 */
@Getter
@Setter
@TableName("agent_user_memory")
public class AgentUserMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer userId;

    private String preferences;

    private String conversationSummary;

    private Date updateTime;
}
