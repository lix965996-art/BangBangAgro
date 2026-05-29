package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 农业知识库文档
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文档标题 */
    private String title;

    /** 分类: crop_disease / irrigation / fertilizer / pest / general */
    private String category;

    /** 文档正文 */
    private String content;

    /** 分块后的检索片段 */
    private String contentChunk;

    /** 向量 embedding (JSON 数组) */
    private String embedding;

    /** 扩展元数据 (JSON) */
    private String metadata;

    /** 来源 */
    private String source;

    /** 创建时间 */
    private Date createdAt;

    /** 更新时间 */
    private Date updatedAt;
}
