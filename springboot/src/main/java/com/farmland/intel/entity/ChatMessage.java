package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

@Data
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer fromUserId;

    private String fromUsername;

    private String fromAvatar;

    private Integer toUserId;

    private Integer toGroupId;

    private String content;

    private String messageType;

    private Integer isRead;

    private LocalDateTime createTime;
}
