package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 账号池配置：多个可互相降级的 provider 端点（base-url + api-key + model）。
 * <p>主备语义由 {@code strategy=failover} 保证：优先用第一个账号，失败熔断后切下一个；
 * {@code strategy=round-robin} 则轮流分摊负载。
 */
@ConfigurationProperties(prefix = "sk-agent.llm.pool")
public class AccountPoolProperties {

    /** 总开关。关闭时回退到单 key（spring.ai.openai 装配的那个模型）。 */
    private boolean enabled = true;

    /** failover=主备降级（固定顺序）；round-robin=轮询负载均衡。 */
    private String strategy = "failover";

    /** 某账号连续失败达到该次数即熔断。 */
    private int maxConsecutiveFailures = 3;

    /** 熔断后的冷却时长（毫秒）。 */
    private long cooldownMs = 30_000;

    private List<Account> accounts = new ArrayList<>();

    public static class Account {
        /** 账号名（仅用于日志与观测）。 */
        private String name;

        /** OpenAI 兼容端点，如 https://api.deepseek.com、https://api.siliconflow.cn/v1 */
        private String baseUrl;

        private String apiKey;

        /** 该账号使用的模型名。 */
        private String model;

        /** 可选：覆盖温度。null 表示沿用模板默认。 */
        private Double temperature;

        /** 可选：覆盖最大 token。null 表示沿用模板默认。 */
        private Integer maxTokens;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public int getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    public void setMaxConsecutiveFailures(int maxConsecutiveFailures) {
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public void setCooldownMs(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    public boolean isRoundRobin() {
        return "round-robin".equalsIgnoreCase(strategy);
    }
}