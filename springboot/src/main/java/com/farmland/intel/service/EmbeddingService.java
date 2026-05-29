package com.farmland.intel.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 服务：使用 Spring AI EmbeddingModel 替代手动 HTTP 调用。
 * 通过 DashScope 的 OpenAI 兼容端点生成 text-embedding-v3 向量。
 */
@Service
@Slf4j
public class EmbeddingService {

    @Value("${qwen.api-key:}")
    private String apiKey;

    @Value("${qwen.embedding-api-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String apiUrl;

    @Autowired(required = false)
    private com.farmland.intel.service.IAiConfigService aiConfigService;

    private static final String EMBEDDING_MODEL = "text-embedding-v3";

    /** 缓存 EmbeddingModel 实例 */
    private volatile EmbeddingModel cachedModel;

    /**
     * 生成单条文本的 embedding
     */
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            return new float[0];
        }

        try {
            EmbeddingModel model = getEmbeddingModel();
            if (model == null) {
                log.warn("EmbeddingModel 未初始化（API Key 未配置）");
                return new float[0];
            }

            float[] embedding = model.embed(text);
            return embedding != null ? embedding : new float[0];
        } catch (Exception e) {
            log.error("调用 Embedding API 异常: {}", e.getMessage());
            return new float[0];
        }
    }

    /**
     * 批量生成 embedding
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
            // 简单限流，避免 API 过载
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return results;
    }

    /**
     * 计算两个向量的余弦相似度
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 获取或创建 EmbeddingModel 实例（懒加载 + 缓存）
     */
    @SuppressWarnings("null")
    private EmbeddingModel getEmbeddingModel() {
        if (cachedModel != null) {
            return cachedModel;
        }

        String key = resolveApiKey();
        if (!StringUtils.hasText(key)) {
            return null;
        }

        synchronized (this) {
            if (cachedModel != null) {
                return cachedModel;
            }

            String baseUrl = apiUrl.replaceAll("/+$", "");

            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(key)
                    .build();

            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model(EMBEDDING_MODEL)
                    .build();

            cachedModel = new OpenAiEmbeddingModel(api, org.springframework.ai.document.MetadataMode.EMBED, options);

            log.info("EmbeddingModel 已初始化: model={}, url={}", EMBEDDING_MODEL, baseUrl);
            return cachedModel;
        }
    }

    private String resolveApiKey() {
        // 优先使用配置的 API Key
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        // 降级：从 DB 获取
        if (aiConfigService != null) {
            try {
                com.farmland.intel.entity.AiConfig cfg = aiConfigService.getByUserId(null);
                if (cfg != null && StringUtils.hasText(cfg.getApiKey())) {
                    return cfg.getApiKey();
                }
            } catch (Exception e) {
                log.debug("从 DB 获取 API Key 失败: {}", e.getMessage());
            }
        }
        return null;
    }
}
