package com.farmland.intel.service.impl;

import cn.hutool.json.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.KnowledgeDocument;
import com.farmland.intel.mapper.KnowledgeDocumentMapper;
import com.farmland.intel.service.EmbeddingService;
import com.farmland.intel.service.IKnowledgeService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 农业知识库服务实现。
 *
 * <p>P0 改造: 加内存缓存。
 * <ul>
 *   <li>启动时一次性把所有有 embedding 的文档加载进 {@link #embeddingCache}</li>
 *   <li>搜索时直接遍历内存缓存,不再每次查库 + JSON 解析(原代码每次搜索拉全表 + N 次 JSON parse,知识库一大就 OOM)</li>
 *   <li>写入(生成/更新 embedding)时同步维护缓存,避免陈旧</li>
 *   <li>关键词兜底搜索加 LIMIT 500,防止全表扫描</li>
 * </ul>
 *
 * <p>性能对比 (假设 1000 篇文档,1024 维向量):
 * <ul>
 *   <li>改前: 每次搜索 拉 4MB + 1000 次 JSON.parse(每次 ~0.5ms) ≈ 500ms+ CPU</li>
 *   <li>改后: 每次搜索 1000 次 float[] 点积,纯 CPU ≈ 5-10ms</li>
 * </ul>
 *
 * <p>缓存一致性: 单实例部署下完全一致。多实例部署需要外部缓存(如 Redis)
 * 或者依赖 {@link #refreshCache()} 手动刷新。
 */
@Service
@Slf4j
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements IKnowledgeService {

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 向量缓存: docId → (向量, 分类)。
     * ConcurrentHashMap 保证读写并发安全,无需额外锁。
     */
    private final Map<Long, EmbeddingCacheEntry> embeddingCache = new ConcurrentHashMap<>();

    /**
     * 文档元信息缓存: docId → 完整 KnowledgeDocument 对象。
     * 搜索结果直接从这里拿,避免额外查库。
     */
    private final Map<Long, KnowledgeDocument> docMetaCache = new ConcurrentHashMap<>();

    /**
     * 容器启动后立即预热缓存。
     * 失败不阻断启动,只会让搜索性能退化到原来的水平。
     */
    @PostConstruct
    public void warmUpCache() {
        try {
            long t0 = System.currentTimeMillis();
            List<KnowledgeDocument> all = list(Wrappers.<KnowledgeDocument>lambdaQuery()
                    .isNotNull(KnowledgeDocument::getEmbedding));
            int loaded = 0;
            for (KnowledgeDocument d : all) {
                float[] v = parseEmbedding(d.getEmbedding());
                if (v.length > 0) {
                    embeddingCache.put(d.getId(), new EmbeddingCacheEntry(v, d.getCategory()));
                    docMetaCache.put(d.getId(), d);
                    loaded++;
                }
            }
            log.info("[知识库缓存] 预热完成 — 加载 {} 篇文档,耗时 {} ms", loaded, System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.warn("[知识库缓存] 预热失败(不影响功能,搜索会自动走慢路径): {}", e.getMessage());
        }
    }

    /**
     * 手动刷新缓存。
     * 用于:多实例部署时定时同步、或管理员在 UI 触发(可暴露成 /api/knowledge/cache/refresh 接口)
     */
    @Override
    public synchronized int refreshCache() {
        embeddingCache.clear();
        docMetaCache.clear();
        warmUpCache();
        return embeddingCache.size();
    }

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

        // P0 改造: 缓存为空时降级到原全表逻辑,保证可用性
        if (embeddingCache.isEmpty()) {
            log.debug("[知识库缓存] 缓存为空,降级到全表查询路径");
            return searchByFullScan(queryEmbedding, category, topK);
        }

        // 主路径: 直接遍历内存缓存,毫秒级返回
        List<ScoredDocument> scored = new ArrayList<>(embeddingCache.size());
        boolean filterByCategory = StringUtils.hasText(category);
        for (Map.Entry<Long, EmbeddingCacheEntry> e : embeddingCache.entrySet()) {
            EmbeddingCacheEntry entry = e.getValue();
            if (filterByCategory && !category.equals(entry.category)) continue;
            KnowledgeDocument doc = docMetaCache.get(e.getKey());
            if (doc == null) continue;
            double similarity = EmbeddingService.cosineSimilarity(queryEmbedding, entry.vector);
            scored.add(new ScoredDocument(doc, similarity));
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
            // P0 改造: 写入后同步维护缓存,避免陈旧
            embeddingCache.put(docId, new EmbeddingCacheEntry(embedding, doc.getCategory()));
            docMetaCache.put(docId, doc);
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

    // ============================================================
    // P0 缓存一致性: override MyBatis-Plus 的删除方法,清理缓存
    // ============================================================

    @Override
    public boolean removeById(java.io.Serializable id) {
        boolean ok = super.removeById(id);
        if (ok && id != null) {
            try {
                Long key = (id instanceof Long) ? (Long) id : Long.valueOf(id.toString());
                embeddingCache.remove(key);
                docMetaCache.remove(key);
            } catch (NumberFormatException ignore) {}
        }
        return ok;
    }

    @Override
    public boolean removeByIds(java.util.Collection<?> ids) {
        boolean ok = super.removeByIds(ids);
        if (ok && ids != null) {
            for (Object id : ids) {
                try {
                    Long key = (id instanceof Long) ? (Long) id : Long.valueOf(id.toString());
                    embeddingCache.remove(key);
                    docMetaCache.remove(key);
                } catch (NumberFormatException ignore) {}
            }
        }
        return ok;
    }

    @Override
    public boolean updateById(KnowledgeDocument entity) {
        boolean ok = super.updateById(entity);
        if (ok && entity != null && entity.getId() != null) {
            // 更新后同步缓存中的元数据(分类、标题等可能变)
            KnowledgeDocument fresh = super.getById(entity.getId());
            if (fresh != null) {
                docMetaCache.put(entity.getId(), fresh);
                // 如果 embedding 没变,沿用旧缓存;变了的话 generateEmbedding 会重新写入
                if (StringUtils.hasText(fresh.getEmbedding()) && !embeddingCache.containsKey(entity.getId())) {
                    float[] v = parseEmbedding(fresh.getEmbedding());
                    if (v.length > 0) {
                        embeddingCache.put(entity.getId(), new EmbeddingCacheEntry(v, fresh.getCategory()));
                    }
                }
            }
        }
        return ok;
    }

    /**
     * 缓存为空时的兜底全表扫描路径(改前的原逻辑)。
     * 保留是为了在第一次 warmUpCache 失败或文档刚导入还没预热时仍可用。
     */
    private List<KnowledgeDocument> searchByFullScan(float[] queryEmbedding, String category, int topK) {
        List<KnowledgeDocument> candidates;
        if (StringUtils.hasText(category)) {
            candidates = list(Wrappers.<KnowledgeDocument>lambdaQuery()
                    .eq(KnowledgeDocument::getCategory, category)
                    .isNotNull(KnowledgeDocument::getEmbedding));
        } else {
            candidates = list(Wrappers.<KnowledgeDocument>lambdaQuery()
                    .isNotNull(KnowledgeDocument::getEmbedding));
        }

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

    /**
     * 关键词降级搜索（当 embedding 不可用时）。
     * P0 改造: 加 LIMIT 500,避免类目为空时全表扫描拖垮 MySQL。
     */
    private List<KnowledgeDocument> searchByKeyword(String query, String category, int topK) {
        List<KnowledgeDocument> candidates;
        if (StringUtils.hasText(category)) {
            candidates = list(Wrappers.<KnowledgeDocument>lambdaQuery()
                    .eq(KnowledgeDocument::getCategory, category)
                    .last("LIMIT 500"));
        } else {
            // P0 改造: 不再 list() 全表,加 500 条上限保护 MySQL
            candidates = list(Wrappers.<KnowledgeDocument>lambdaQuery()
                    .last("LIMIT 500"));
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

    /**
     * 缓存条目: 包含向量本身和分类(用于过滤)。
     * 分类放进缓存避免每次搜索都查 docMetaCache.get(id).getCategory()。
     */
    private static class EmbeddingCacheEntry {
        final float[] vector;
        final String category;

        EmbeddingCacheEntry(float[] v, String c) {
            this.vector = v;
            this.category = c;
        }
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
