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

    /** 视觉(多模态)模型名称，例如 doubao-1.5-vision-pro-32k-250115 / Qwen/Qwen2.5-VL-72B-Instruct（留空则用后端 vision.* 兜底） */
    private String visionModelName;

    /** 视觉模型 BaseURL，例如 https://ark.cn-beijing.volces.com/api/v3 或 https://api.siliconflow.cn/v1 */
    private String visionBaseUrl;

    /** 视觉模型 API Key（独立于主模型，不共用） */
    private String visionApiKey;

    /** 采样温度 */
    private BigDecimal temperature;

    /** 是否启用 */
    private Integer enabled;

    /**
     * AI 写操作审批策略：full_auto(全自动) / semi_approval(半审批,默认) / full_approval(全审批)。
     * 控制 @Tool 写操作(采购/销售/库存/通知)是直接执行还是入队等用户审批。
     */
    private String aiActionPolicy;

    private Date updateTime;
}
