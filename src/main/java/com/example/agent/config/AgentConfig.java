package com.example.agent.config;

import com.example.agent.tools.AttributeRecordTools;
import com.example.agent.tools.BrowserTools;
import com.example.agent.tools.DataQueryTools;
import com.example.agent.tools.DecisionTools;
import com.example.agent.tools.LayerRecordTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 配置。
 * 基于 OpenAI 兼容云端模型（base-url / api-key 外置，支持切换任意兼容厂商）。
 * 按三阶段（分层 / 属性 / 筛选）分别绑定不同的工具集。
 */
@Configuration
public class AgentConfig {

    @Bean
    public ChatClient layeringChatClient(
            OpenAiChatModel chatModel,
            DataQueryTools queryTools,
            LayerRecordTools layerTools) {
        return ChatClient.builder(chatModel)
                .defaultTools(queryTools, layerTools)
                .build();
    }

    @Bean
    public ChatClient attributeChatClient(
            OpenAiChatModel chatModel,
            DataQueryTools queryTools,
            AttributeRecordTools attrTools) {
        return ChatClient.builder(chatModel)
                .defaultTools(queryTools, attrTools)
                .build();
    }

    @Bean
    public ChatClient filterChatClient(
            OpenAiChatModel chatModel,
            DataQueryTools queryTools,
            DecisionTools decisionTools) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultTools(queryTools, decisionTools)
                .build();
    }

    @Bean
    public ChatClient planningChatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public ChatClient browserChatClient(
            OpenAiChatModel chatModel,
            BrowserTools browserTools) {
        return ChatClient.builder(chatModel)
                .defaultTools(browserTools)
                .build();
    }
}