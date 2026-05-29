package com.farmland.intel.service.impl;

import cn.hutool.json.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.KnowledgeDocument;
import com.farmland.intel.mapper.KnowledgeDocumentMapper;
import com.farmland.intel.service.EmbeddingService;
import com.farmland.intel.service.IKnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements IKnowledgeService {

    @Autowired
    private EmbeddingService embeddingService;

    @Override
    public List<KnowledgeDocument> search(String query, String category, int topK) {
        if (!StringUtils.hasText(query)) {
            return new ArrayList<>();
        }

        // 生成查询向量
        float[] queryEmbedding = embeddingService.embed(query);
        if (queryEmbedding.length == 0) {
            log.warn("查询向量生成失败，降级为关键词匹配");
            return searchByKeyword(query, category, topK);
        }

        // 获取候选文档
        List<KnowledgeDocument> candidates;
        if (StringUtils.hasText(category)) {
            candidates = list(Wrappers.<KnowledgeDocument>lambdaQuery()
                    .eq(KnowledgeDocument::getCategory, category)
                    .isNotNull(KnowledgeDocument::getEmbedding));
        } else {
            candidates = list(Wrappers.<KnowledgeDocument>lambdaQuery()
                    .isNotNull(KnowledgeDocument::getEmbedding));
        }

        // 计算相似度并排序
        List<ScoredDocument> scored = new ArrayList<>();
        for (KnowledgeDocument doc : candidates) {
            float[] docEmbedding = parseEmbedding(doc.getEmbedding());
            if (docEmbedding.length > 0) {
                double similarity = EmbeddingService.cosineSimilarity(queryEmbedding, docEmbedding);
                scored.add(new ScoredDocument(doc, similarity));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredDocument::getScore).reversed());

        return scored.stream()
                .limit(topK)
                .map(ScoredDocument::getDoc)
                .collect(Collectors.toList());
    }

    @Override
    public void generateEmbedding(Long docId) {
        KnowledgeDocument doc = getById(docId);
        if (doc == null) {
            log.warn("文档不存在: {}", docId);
            return;
        }

        // 使用 content_chunk 或 content 生成 embedding
        String text = StringUtils.hasText(doc.getContentChunk()) ? doc.getContentChunk() : doc.getContent();
        if (!StringUtils.hasText(text)) {
            log.warn("文档内容为空: {}", docId);
            return;
        }

        // 截断到 2000 字符（Embedding API 限制）
        if (text.length() > 2000) {
            text = text.substring(0, 2000);
        }

        float[] embedding = embeddingService.embed(text);
        if (embedding.length > 0) {
            doc.setEmbedding(floatArrayToJson(embedding));
            updateById(doc);
            log.debug("文档 {} embedding 生成成功，维度: {}", docId, embedding.length);
        } else {
            log.warn("文档 {} embedding 生成失败", docId);
        }
    }

    @Override
    public int generateAllPendingEmbeddings() {
        List<KnowledgeDocument> pending = list(Wrappers.<KnowledgeDocument>lambdaQuery()
                .isNull(KnowledgeDocument::getEmbedding));
        int count = 0;
        for (KnowledgeDocument doc : pending) {
            generateEmbedding(doc.getId());
            count++;
        }
        return count;
    }

    @Override
    public List<KnowledgeDocument> getByCategory(String category) {
        return list(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(StringUtils.hasText(category), KnowledgeDocument::getCategory, category)
                .orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    /**
     * 关键词降级搜索（当 embedding 不可用时）
     */
    private List<KnowledgeDocument> searchByKeyword(String query, String category, int topK) {
        List<KnowledgeDocument> candidates;
        if (StringUtils.hasText(category)) {
            candidates = list(Wrappers.<KnowledgeDocument>lambdaQuery()
                    .eq(KnowledgeDocument::getCategory, category));
        } else {
            candidates = list();
        }

        String lowerQuery = query.toLowerCase();
        return candidates.stream()
                .filter(d -> {
                    String title = d.getTitle() != null ? d.getTitle().toLowerCase() : "";
                    String content = d.getContentChunk() != null ? d.getContentChunk().toLowerCase() : "";
                    return title.contains(lowerQuery) || content.contains(lowerQuery);
                })
                .limit(topK)
                .collect(Collectors.toList());
    }

    private float[] parseEmbedding(String json) {
        if (!StringUtils.hasText(json)) return new float[0];
        try {
            JSONArray arr = cn.hutool.json.JSONUtil.parseArray(json);
            float[] result = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                result[i] = arr.getFloat(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            log.debug("解析 embedding 失败: {}", e.getMessage());
            return new float[0];
        }
    }

    private String floatArrayToJson(float[] arr) {
        JSONArray jsonArr = new JSONArray();
        for (float v : arr) {
            jsonArr.add(v);
        }
        return jsonArr.toString();
    }

    private static class ScoredDocument {
        private final KnowledgeDocument doc;
        private final double score;

        ScoredDocument(KnowledgeDocument doc, double score) {
            this.doc = doc;
            this.score = score;
        }

        public KnowledgeDocument getDoc() { return doc; }
        public double getScore() { return score; }
    }
}
