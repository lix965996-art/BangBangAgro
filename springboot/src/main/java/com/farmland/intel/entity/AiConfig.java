package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("sys_ai_config")
public class AiConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 关联用户 */
    private Integer userId;

    /**
     * 提供商标识：qwen | deepseek | glm | minimax | openai | custom
     */
    private String provider;

    /**
     * API BaseURL（不含 /chat/completions）
     * 例如：https://api.deepseek.com/v1
     */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 规划/推理模型名称，例如 deepseek-reasoner / qwen-max */
    private String modelName;

    /** 对话模型名称（留空则沿用 modelName） */
    private String chatModelName;

    /** 对话模型 BaseURL（留空则沿用 baseUrl） */
    private String chatBaseUrl;

    /** 对话模型 API Key（留空则沿用 apiKey） */
    private String chatApiKey;

    /** 采样温度 */
    private BigDecimal temperature;

    /** 是否启用 */
    private Integer enabled;

    private Date updateTime;
}
