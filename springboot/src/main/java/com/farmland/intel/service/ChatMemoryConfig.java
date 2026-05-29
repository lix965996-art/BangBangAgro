package com.farmland.intel.service;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Spring AI 对话记忆配置。
 * 使用 JdbcChatMemoryRepository 将对话消息存储到数据库，
 * MessageWindowChatMemory 保留最近 20 条消息。
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public JdbcChatMemoryRepository chatMemoryRepository(DataSource dataSource) {
        return JdbcChatMemoryRepository.builder()
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }
}
