package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * provider 级 LLM 请求重试策略配置，参数语义照搬 deepseek-harness 的 llm-retry：
 *   initialDelayMs / maxDelayMs / jitterRatio / maxRetries 构成指数退避 + 抖动。
 * <p>与 harness 的差异（安全取舍）：本实现无论 normal / always 都用 {@code maxRetries} 封顶，
 * 避免服务端出现无界重试。mode=normal 仅重试「网络态瞬断」（DNS/连接/超时）；mode=always 额外把
 * 可识别状态码的 HTTP 错误也纳入重试。
 */
@ConfigurationProperties(prefix = "sk-agent.llm.retry")
public class LlmRetryProperties {

    /** 总开关。 */
    private boolean enabled = true;

    /** normal=仅网络态瞬断重试；always=网络态 + 可识别状态码一并重试（均受 maxRetries 封顶）。 */
    private String mode = "normal";

    /** 首次重试的基准延迟（毫秒）。 */
    private long initialDelayMs = 500;

    /** 单次重试延迟上限（毫秒）。 */
    private long maxDelayMs = 8000;

    /** 抖动比例，取值 [0,1]。实际延迟 = exponential * (1 - jitterRatio + 2*jitterRatio*random)。 */
    private double jitterRatio = 0.25;

    /** 同一请求最大重试次数。 */
    private int maxRetries = 3;

    /** mode=always 时视为可重试的 HTTP 状态码。 */
    private List<Integer> retryableCodes = new ArrayList<>(List.of(429, 500, 502, 503, 504));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    public double getJitterRatio() {
        return jitterRatio;
    }

    public void setJitterRatio(double jitterRatio) {
        this.jitterRatio = jitterRatio;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public List<Integer> getRetryableCodes() {
        return retryableCodes;
    }

    public void setRetryableCodes(List<Integer> retryableCodes) {
        this.retryableCodes = retryableCodes;
    }

    public boolean isAlwaysMode() {
        return "always".equalsIgnoreCase(mode);
    }
}