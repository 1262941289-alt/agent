package com.example.agent.config;

import com.example.agent.llm.PooledChatModel;
import com.example.agent.llm.RetryingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * 装配 provider 级重试 + 多 key 账号池，作为 {@code @Primary ChatModel} 暴露，
 * 使 {@code AgentConfig} 里所有 ChatClient 统一走「账号池 → 单账号指数退避重试」管线。
 * <p>账号池未启用或未配置账号时，回退到单 key 的重试模型。
 */
@Configuration
@EnableConfigurationProperties({LlmRetryProperties.class, AccountPoolProperties.class})
public class LlmPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmPoolConfig.class);

    @Bean
    @Primary
    public ChatModel pooledChatModel(OpenAiChatModel template,
                                     LlmRetryProperties retryProps,
                                     AccountPoolProperties poolProps) {
        List<PooledChatModel.AccountHandle> handles = buildHandles(template, retryProps, poolProps);
        if (handles.isEmpty()) {
            log.info("[llm-pool] 账号池未配置有效账号，回退单 key 重试模型");
            return new RetryingChatModel(template, retryProps);
        }
        log.info("[llm-pool] 账号池装配 {} 个账号，策略={}, 熔断阈值={}, 冷却={}ms",
                handles.size(), poolProps.getStrategy(),
                poolProps.getMaxConsecutiveFailures(), poolProps.getCooldownMs());
        return new PooledChatModel(handles, poolProps);
    }

    private List<PooledChatModel.AccountHandle> buildHandles(OpenAiChatModel template,
                                                              LlmRetryProperties retryProps,
                                                              AccountPoolProperties poolProps) {
        List<PooledChatModel.AccountHandle> handles = new ArrayList<>();
        if (!poolProps.isEnabled()) {
            return handles;
        }
        for (AccountPoolProperties.Account acc : poolProps.getAccounts()) {
            if (acc.getApiKey() == null || acc.getApiKey().isBlank()) {
                log.warn("[llm-pool] 账号 {} 缺少 api-key，跳过", acc.getName());
                continue;
            }
            OpenAiChatModel accountModel = cloneForAccount(template, acc);
            ChatModel retried = new RetryingChatModel(accountModel, retryProps);
            handles.add(new PooledChatModel.AccountHandle(acc.getName(), retried));
        }
        return handles;
    }

    /**
     * 以自动装配的模板为底，克隆出绑定到指定账号（base-url/api-key/model）的模型。
     * <p>通过 {@code mutate()} 复用模板的 ToolCallingManager / RetryTemplate / ObservationRegistry，
     * 避免重建工具调用管线；仅替换底层 {@link OpenAiApi} 与默认选项。
     */
    private OpenAiChatModel cloneForAccount(OpenAiChatModel template, AccountPoolProperties.Account acc) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(acc.getBaseUrl())
                .apiKey(acc.getApiKey())
                .build();

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(acc.getModel())
                // 保持工具调用开关，保证账号模型同样能走 function calling
                .internalToolExecutionEnabled(true);
        if (acc.getTemperature() != null) {
            options.temperature(acc.getTemperature());
        }
        if (acc.getMaxTokens() != null) {
            options.maxTokens(acc.getMaxTokens());
        }

        return template.mutate()
                .openAiApi(api)
                .defaultOptions(options.build())
                .build();
    }
}