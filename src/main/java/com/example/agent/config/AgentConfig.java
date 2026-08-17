package com.example.agent.config;

import com.example.agent.tools.AttributeRecordTools;
import com.example.agent.tools.DataQueryTools;
import com.example.agent.tools.DecisionTools;
import com.example.agent.tools.LayerRecordTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 配置。
 * 基于 Ollama 本地模型 qwen2.5:1.5b（支持 Function Calling）。
 * 按三阶段（分层 / 属性 / 筛选）分别绑定不同的工具集。
 */
@Configuration
public class AgentConfig {

    @Bean
    public ChatClient layeringChatClient(
            OllamaChatModel chatModel,
            DataQueryTools queryTools,
            LayerRecordTools layerTools) {
        return ChatClient.builder(chatModel)
                .defaultTools(queryTools, layerTools)
                .build();
    }

    @Bean
    public ChatClient attributeChatClient(
            OllamaChatModel chatModel,
            DataQueryTools queryTools,
            AttributeRecordTools attrTools) {
        return ChatClient.builder(chatModel)
                .defaultTools(queryTools, attrTools)
                .build();
    }

    @Bean
    public ChatClient filterChatClient(
            OllamaChatModel chatModel,
            DataQueryTools queryTools,
            DecisionTools decisionTools) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultTools(queryTools, decisionTools)
                .build();
    }
}
