package com.example.agent.config;

import com.example.agent.service.ToolExecutionService;
import com.example.agent.tools.AttributeRecordTools;
import com.example.agent.tools.BrowserTools;
import com.example.agent.tools.DataQueryTools;
import com.example.agent.tools.DecisionTools;
import com.example.agent.tools.GuardedToolCallback;
import com.example.agent.tools.HistoryTools;
import com.example.agent.tools.LayerRecordTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

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
            LayerRecordTools layerTools,
            ToolExecutionService toolExecutionService) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(guarded(toolExecutionService, queryTools, layerTools))
                .build();
    }

    @Bean
    public ChatClient attributeChatClient(
            OpenAiChatModel chatModel,
            DataQueryTools queryTools,
            AttributeRecordTools attrTools,
            ToolExecutionService toolExecutionService) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(guarded(toolExecutionService, queryTools, attrTools))
                .build();
    }

    @Bean
    public ChatClient filterChatClient(
            OpenAiChatModel chatModel,
            DataQueryTools queryTools,
            DecisionTools decisionTools,
            ToolExecutionService toolExecutionService) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultToolCallbacks(guarded(toolExecutionService, queryTools, decisionTools))
                .build();
    }

    @Bean
    public ChatClient planningChatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public ChatClient browserChatClient(
            OpenAiChatModel chatModel,
            BrowserTools browserTools,
            ToolExecutionService toolExecutionService) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(guarded(toolExecutionService, browserTools))
                .build();
    }

    @Bean
    public ChatClient dataChatClient(
            OpenAiChatModel chatModel,
            DataQueryTools queryTools,
            LayerRecordTools layerTools,
            AttributeRecordTools attrTools,
            DecisionTools decisionTools,
            ToolExecutionService toolExecutionService) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(guarded(toolExecutionService, queryTools, layerTools, attrTools, decisionTools))
                .build();
    }

    @Bean
    public ChatClient historyChatClient(
            OpenAiChatModel chatModel,
            HistoryTools historyTools,
            ToolExecutionService toolExecutionService) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(guarded(toolExecutionService, historyTools))
                .build();
    }

    /** 把原生工具 POJO 语义渲染成 ToolCallback 后逐个包上 {@link GuardedToolCallback}，统一走守护管线。 */
    private ToolCallback[] guarded(ToolExecutionService svc, Object... tools) {
        ToolCallback[] raw = ToolCallbacks.from(tools);
        return Arrays.stream(raw)
                .map(cb -> new GuardedToolCallback(cb, svc))
                .toArray(ToolCallback[]::new);
    }
}