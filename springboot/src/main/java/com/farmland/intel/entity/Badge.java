package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("badge")
@Schema(description = "成就徽章")
public class Badge implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "用户id")
    private Integer userId;

    @Schema(description = "徽章类型")
    private String badgeType;

    @Schema(description = "当前进度")
    private Integer progress;

    @Schema(description = "目标值")
    private Integer target;

    @Schema(description = "是否解锁(0/1)")
    private Integer unlocked;

    @Schema(description = "解锁时间")
    private LocalDateTime unlockedAt;
}
