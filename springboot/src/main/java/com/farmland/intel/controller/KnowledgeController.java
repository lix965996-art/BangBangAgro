package com.farmland.intel.controller;

import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.KnowledgeDocument;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IKnowledgeService;
import com.farmland.intel.utils.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 农业知识库管理接口
 */
@RestController
@RequestMapping("/api/knowledge")
@Slf4j
public class KnowledgeController {

    @Autowired
    private IKnowledgeService knowledgeService;

    /**
     * 搜索知识库
     */
    @GetMapping("/search")
    public Result search(@RequestParam String query,
                         @RequestParam(required = false) String category,
                         @RequestParam(defaultValue = "5") int topK) {
        if (!StringUtils.hasText(query)) {
            return Result.error(Constants.CODE_400, "query 不能为空");
        }
        List<KnowledgeDocument> results = knowledgeService.search(query, category, Math.min(topK, 20));
        return Result.success(results);
    }

    /**
     * 获取文档列表（按分类）
     */
    @GetMapping("/documents")
    public Result getDocuments(@RequestParam(required = false) String category) {
        List<KnowledgeDocument> docs = knowledgeService.getByCategory(category);
        return Result.success(docs);
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/document/{id}")
    public Result getDocument(@PathVariable Long id) {
        KnowledgeDocument doc = knowledgeService.getById(id);
        if (doc == null) {
            return Result.error(Constants.CODE_404, "文档不存在");
        }
        return Result.success(doc);
    }

    /**
     * 创建知识文档
     */
    @PostMapping("/document")
    public Result createDocument(@RequestBody KnowledgeDocument doc) {
        if (!StringUtils.hasText(doc.getTitle()) || !StringUtils.hasText(doc.getContent())) {
            return Result.error(Constants.CODE_400, "标题和内容不能为空");
        }
        if (!StringUtils.hasText(doc.getCategory())) {
            doc.setCategory("general");
        }
        // 自动生成 content_chunk（取前 500 字符）
        String chunk = doc.getContent().length() > 500
                ? doc.getContent().substring(0, 500)
                : doc.getContent();
        doc.setContentChunk(chunk);
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null) {
            doc.setCreatedBy(currentUser.getId());
            doc.setUpdatedBy(currentUser.getId());
        }
        knowledgeService.save(doc);
        return Result.success(doc);
    }

    /**
     * 更新知识文档
     */
    @PutMapping("/document/{id}")
    public Result updateDocument(@PathVariable Long id, @RequestBody KnowledgeDocument doc) {
        KnowledgeDocument existing = knowledgeService.getById(id);
        if (existing == null) {
            return Result.error(Constants.CODE_404, "文档不存在");
        }
        doc.setId(id);
        if (StringUtils.hasText(doc.getContent())) {
            String chunk = doc.getContent().length() > 500
                    ? doc.getContent().substring(0, 500)
                    : doc.getContent();
            doc.setContentChunk(chunk);
            // 内容变更后清除旧 embedding，等待重新生成
            doc.setEmbedding(null);
        }
        // created_by 由 FieldStrategy.NEVER 保护,不会被 updateById 覆盖; 这里只记 updated_by
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null) {
            doc.setUpdatedBy(currentUser.getId());
        }
        knowledgeService.updateById(doc);
        return Result.success(doc);
    }

    /**
     * 删除知识文档
     */
    @DeleteMapping("/document/{id}")
    public Result deleteDocument(@PathVariable Long id) {
        knowledgeService.removeById(id);
        return Result.success("删除成功");
    }

    /**
     * 为指定文档生成 embedding
     */
    @PostMapping("/document/{id}/embedding")
    public Result generateEmbedding(@PathVariable Long id) {
        knowledgeService.generateEmbedding(id);
        return Result.success("embedding 生成完成");
    }

    /**
     * 批量为所有未生成 embedding 的文档生成向量
     */
    @PostMapping("/generate-embeddings")
    public Result generateAllEmbeddings() {
        int count = knowledgeService.generateAllPendingEmbeddings();
        return Result.success("处理完成，共生成 " + count + " 个 embedding");
    }

    /**
     * 手动刷新内存缓存(P0 优化配套)
     * 适用场景:外部脚本直接修改数据库后,触发应用层同步
     */
    @PostMapping("/cache/refresh")
    public Result refreshCache() {
        int n = knowledgeService.refreshCache();
        return Result.success(n, "缓存已刷新,当前加载 " + n + " 篇文档");
    }
}
