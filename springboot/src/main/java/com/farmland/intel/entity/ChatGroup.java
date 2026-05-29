package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

@Data
@TableName("chat_group")
public class ChatGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String name;

    private String avatarUrl;

    private Integer ownerId;

    private String ownerName;

    private LocalDateTime createTime;

    /** 10位唯一群号，用于加群 */
    private String groupNumber;
}
