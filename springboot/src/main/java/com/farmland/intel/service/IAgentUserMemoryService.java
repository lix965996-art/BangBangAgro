package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.AgentUserMemory;

/**
 * Agent 用户记忆：写入偏好、滚动记录对话，供 system prompt 引用。
 */
public interface IAgentUserMemoryService extends IService<AgentUserMemory> {

    /**
     * 按用户取或创建一行。
     */
    AgentUserMemory getOrCreate(Integer userId);

    /**
     * 拼成一段注入 system prompt 的文本；无内容时返回 null。
     */
    String buildPromptSnippet(Integer userId);

    /**
     * 在对话摘要末尾追加一轮 Q/A（过长时保留尾部）。
     */
    void appendConversationTurn(Integer userId, String question, String advice);

    /**
     * 追加一条用户显式偏好说明（过长时保留尾部）。
     */
    void appendPreferenceNote(Integer userId, String note);

    /**
     * 清空持久记忆。
     *
     * @param scope {@code all} | {@code preferences} | {@code summary}
     */
    void clearMemory(Integer userId, String scope);
}
