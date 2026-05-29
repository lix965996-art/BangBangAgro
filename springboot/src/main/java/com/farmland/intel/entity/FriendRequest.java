package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("friend_request")
public class FriendRequest implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer fromUserId;

    private Integer toUserId;

    /** pending / accepted / rejected */
    private String status;

    private LocalDateTime createTime;
}
