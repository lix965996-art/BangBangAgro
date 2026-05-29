package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 *
 * </p>
 *
 * @author
 * @since 2022-01-26
 */
@Getter
@Setter
@TableName("sys_user")
@Schema(description = "User对象")
@ToString
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "头像")
    private String avatarUrl;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "帮帮农ID（6位唯一数字，用于加好友）")
    private String uid;

    @Schema(description = "账号状态：0正常 1封禁")
    private Integer status;

}
