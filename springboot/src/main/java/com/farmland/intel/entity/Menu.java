package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 *
 * </p>
 *
 * @author
 * @since 2022-02-10
 */
@Getter
@Setter
@TableName("sys_menu")
@Schema(description = "Menu对象")
public class Menu implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "路径")
    private String path;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "描述")
    private String description;

    @TableField(exist = false)
    private List<Menu> children;

    private Integer pid;

    private String pagePath;
    private String sortNum;


}
