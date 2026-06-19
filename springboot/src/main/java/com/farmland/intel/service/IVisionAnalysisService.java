package com.farmland.intel.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 多模态视觉分析服务（OpenAI 兼容，可配豆包/火山方舟、Qwen-VL、硅基流动等）
 * <p>
 * 直接把作物图像交给多模态大模型，一次性给出成熟度估算 + 病虫害判读 + 多维综合分析，
 * 相比固定 4 作物 / 二分类的本地 YOLO，覆盖面更广、无需额外部署推理服务。
 */
public interface IVisionAnalysisService {

    /**
     * 是否已配置可用（api-key 与 model 均非空）。
     */
    boolean isConfigured();

    /**
     * 当前使用的模型标识（用于前端展示，未配置时返回空串）。
     */
    String modelName();

    /**
     * 对单张图片做综合视觉分析（成熟度 + 病虫害 + 多维判读）。
     *
     * @param file         上传的图片
     * @param cropTypeHint 用户标注的作物类型（可空，仅作提示，模型以实际识别为准）
     * @return 与 YOLO「双检」一致结构的结果 Map，额外含 dimensions / ai_summary / ai_advice
     * @throws Exception 配置缺失、网络异常或模型返回异常时抛出，由 Controller 兜底
     */
    Map<String, Object> analyze(MultipartFile file, String cropTypeHint) throws Exception;

    /**
     * 针对单张图片的自由问答（多模态相对 YOLO 的核心差异：可问任意问题、可追问）。
     *
     * @param file     图片
     * @param question 用户问题
     * @param history  之前的问答文本（可空，用于追问时携带上下文）
     * @return 模型的中文回答（纯文本）
     * @throws Exception 配置缺失、参数缺失或调用异常时抛出
     */
    String ask(MultipartFile file, String question, String history) throws Exception;
}
