package com.farmland.intel.service;

import com.farmland.intel.entity.AiConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 编程式构建 Spring AI ChatClient，支持 per-user 配置。
 * 所有 6 个提供商（Qwen/DeepSeek/GLM/MiniMax/OpenAI/Custom）都走 OpenAI 兼容接口。
 */
@Component
public class ChatModelFactory {

    @Resource
    private IAiConfigService aiConfigService;

    @Resource
    private ChatMemory chatMemory;

    /** 缓存：key = "userId:provider:model" */
    private final ConcurrentHashMap<String, ChatClient> cache = new ConcurrentHashMap<>();

    /**
     * 根据 AiConfig 构建或获取缓存的 ChatClient
     */
    public ChatClient getChatClient(AiConfig aiConfig) {
        if (aiConfig == null || aiConfig.getBaseUrl() == null || aiConfig.getApiKey() == null) {
            throw new IllegalArgumentException("AI 配置不完整：baseUrl 和 apiKey 不能为空");
        }

        String cacheKey = buildCacheKey(aiConfig);
        return cache.computeIfAbsent(cacheKey, k -> buildChatClient(aiConfig));
    }

    /**
     * 根据 userId 获取 ChatClient（自动解析用户配置）
     */
    public ChatClient getChatClientForUser(Integer userId) {
        AiConfig aiConfig = aiConfigService.getByUserId(userId);
        return getChatClient(aiConfig);
    }

    /**
     * 获取轻量聊天模型的 ChatClient（使用 chatModelName/chatBaseUrl/chatApiKey，留空则回退到主模型）
     */
    public ChatClient getChatClientForChatMode(AiConfig aiConfig) {
        AiConfig chatConfig = resolveChatConfig(aiConfig);
        return getChatClient(chatConfig);
    }

    /**
     * 使指定用户的缓存失效（配置变更时调用）
     */
    public void evictCache(Integer userId) {
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(userId + ":"));
    }

    /**
     * 清空全部缓存
     */
    public void evictAll() {
        cache.clear();
    }

    // ==================== 内部方法 ====================

    @SuppressWarnings("null")
    private ChatClient buildChatClient(AiConfig aiConfig) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(aiConfig.getBaseUrl())
                .apiKey(aiConfig.getApiKey())
                .build();

        BigDecimal temp = aiConfig.getTemperature() != null
                ? aiConfig.getTemperature()
                : new BigDecimal("0.42");

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(aiConfig.getModelName())
                .temperature(temp.doubleValue())
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();

        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 解析聊天模式配置：chatModelName/chatBaseUrl/chatApiKey 留空时回退到主模型
     */
    private AiConfig resolveChatConfig(AiConfig aiConfig) {
        AiConfig chatConfig = new AiConfig();
        chatConfig.setUserId(aiConfig.getUserId());
        chatConfig.setProvider(aiConfig.getProvider());

        chatConfig.setBaseUrl(
                (aiConfig.getChatBaseUrl() != null && !aiConfig.getChatBaseUrl().isBlank())
                        ? aiConfig.getChatBaseUrl()
                        : aiConfig.getBaseUrl());

        chatConfig.setApiKey(
                (aiConfig.getChatApiKey() != null && !aiConfig.getChatApiKey().isBlank())
                        ? aiConfig.getChatApiKey()
                        : aiConfig.getApiKey());

        chatConfig.setModelName(
                (aiConfig.getChatModelName() != null && !aiConfig.getChatModelName().isBlank())
                        ? aiConfig.getChatModelName()
                        : aiConfig.getModelName());

        chatConfig.setTemperature(aiConfig.getTemperature());
        return chatConfig;
    }

    private String buildCacheKey(AiConfig aiConfig) {
        return aiConfig.getUserId() + ":" + aiConfig.getProvider() + ":" + aiConfig.getModelName();
    }
}
