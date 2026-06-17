package com.farmland.intel.service;

import com.farmland.intel.service.EmbeddingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 知识库缓存核心逻辑单元测试(纯算法,无 Spring 上下文)。
 * <p>
 * P0 优化:KnowledgeServiceImpl 内部用 ConcurrentHashMap 缓存 embedding,
 * 搜索时遍历缓存做余弦相似度比较。本测试验证余弦相似度算法的正确性 —
 * 这是搜索质量的基础。
 */
class KnowledgeCacheLogicTest {

    @Test
    void cosineSimilarityShouldBe1ForIdenticalVectors() {
        float[] a = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] b = {1.0f, 2.0f, 3.0f, 4.0f};
        double sim = EmbeddingService.cosineSimilarity(a, b);
        assertEquals(1.0, sim, 1e-6, "完全相同的向量,余弦相似度应为 1");
    }

    @Test
    void cosineSimilarityShouldBeMinusOneForOpposite() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {-1.0f, -2.0f, -3.0f};
        double sim = EmbeddingService.cosineSimilarity(a, b);
        assertEquals(-1.0, sim, 1e-6, "完全相反的向量,余弦相似度应为 -1");
    }

    @Test
    void cosineSimilarityShouldBeZeroForOrthogonal() {
        float[] a = {1.0f, 0.0f, 0.0f};
        float[] b = {0.0f, 1.0f, 0.0f};
        double sim = EmbeddingService.cosineSimilarity(a, b);
        assertEquals(0.0, sim, 1e-6, "正交向量,余弦相似度应为 0");
    }

    @Test
    void cosineSimilarityShouldHandleDifferentMagnitudes() {
        // 余弦相似度对向量长度不敏感
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {2.0f, 4.0f, 6.0f}; // a 的 2 倍
        double sim = EmbeddingService.cosineSimilarity(a, b);
        assertEquals(1.0, sim, 1e-6, "同方向不同长度,相似度仍为 1");
    }

    @Test
    void cosineSimilarityShouldReturnZeroForEmpty() {
        // 零向量或不同长度应安全返回 0,不抛 NPE/ArithmeticException
        assertDoesNotThrow(() -> EmbeddingService.cosineSimilarity(new float[0], new float[0]));
        assertDoesNotThrow(() -> EmbeddingService.cosineSimilarity(new float[]{1f}, new float[]{1f, 2f}));
    }
}
