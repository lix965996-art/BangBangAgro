package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author 
 * @since 2023-03-14
 */
@Getter
@Setter
@Schema(description = "Notice对象")
public class Notice implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "标题")
    private String name;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "发布时间")
    private String time;

    @Schema(description = "发布人")
    private String user;

    @Schema(description = "封面")
    private String img;


}
