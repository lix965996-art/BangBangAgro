package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    @Schema(description = "最后登录时间")
    private Date lastLoginTime;

    @Schema(description = "最后登录IP")
    private String lastLoginIp;

    @Schema(description = "最后登录IP归属地(高德解析,城市级大致)")
    private String lastLoginRegion;

    @Schema(description = "安全问题")
    private String securityQuestion;

    @Schema(description = "安全问题答案")
    private String securityAnswer;

    @Schema(description = "周滚动评分总分 0-100 (透传, 非持久化)")
    @TableField(exist = false)
    private Double scoreTotal;

    @Schema(description = "评级 S/A/B/C/D (透传, 非持久化)")
    @TableField(exist = false)
    private String scoreGrade;

}
