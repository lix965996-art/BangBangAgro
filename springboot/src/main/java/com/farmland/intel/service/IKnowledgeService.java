package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.KnowledgeDocument;

import java.util.List;

/**
 * 农业知识库服务
 */
public interface IKnowledgeService extends IService<KnowledgeDocument> {

    /**
     * 搜索知识库（向量相似度）
     * @param query 查询文本
     * @param category 分类过滤（可选）
     * @param topK 返回数量
     * @return 相关文档列表
     */
    List<KnowledgeDocument> search(String query, String category, int topK);

    /**
     * 为文档生成 embedding 并保存
     */
    void generateEmbedding(Long docId);

    /**
     * 批量为所有未生成 embedding 的文档生成向量
     * @return 处理数量
     */
    int generateAllPendingEmbeddings();

    /**
     * 按分类获取文档列表
     */
    List<KnowledgeDocument> getByCategory(String category);
}
