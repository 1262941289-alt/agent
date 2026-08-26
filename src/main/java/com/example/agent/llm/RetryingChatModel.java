package com.example.agent.llm;

import com.example.agent.config.LlmRetryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 带重试的 {@link ChatModel} 装饰器：对底层模型（OpenAiChatModel）的每次请求做 provider 级重试。
 * <p>重试时序照搬 deepseek-harness 的 llm-retry.localDelay：
 * <pre>
 *   exponential = min(initialDelayMs * 2^(retry-1), maxDelayMs)
 *   jitter      = 1 - jitterRatio + 2 * jitterRatio * random()
 *   delay       = min(exponential * jitter, maxDelayMs)
 * </pre>
 * <p>重试目标聚焦「网络态瞬断」：DNS 解析失败（UnresolvedAddressException）、连接被拒、读写超时——
 * 这些正是此前 agent 卡住的根因；API 业务错误（无状态码的 OpenAiApiClientErrorException）默认不重试。
 */
public class RetryingChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(RetryingChatModel.class);

    private final ChatModel delegate;
    private final LlmRetryProperties props;

    public RetryingChatModel(ChatModel delegate, LlmRetryProperties props) {
        this.delegate = delegate;
        this.props = props;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if (!props.isEnabled()) {
            return delegate.call(prompt);
        }
        int attempt = 0;
        while (true) {
            try {
                return delegate.call(prompt);
            } catch (RuntimeException e) {
                if (!isTransient(e) || attempt >= props.getMaxRetries()) {
                    throw e;
                }
                attempt++;
                long delayMs = computeDelayMills(attempt);
                log.warn("[llm-retry] 第 {}/{} 次重试，延迟 {}ms（{}）", attempt, props.getMaxRetries(), delayMs, rootMessage(e));
                sleepUninterruptibly(delayMs);
            }
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        if (!props.isEnabled()) {
            return delegate.stream(prompt);
        }
        return Flux.defer(() -> delegate.stream(prompt))
                .retryWhen(Retry.backoff(props.getMaxRetries(), Duration.ofMillis(props.getInitialDelayMs()))
                        .jitter(props.getJitterRatio() * 2)
                        .maxBackoff(Duration.ofMillis(props.getMaxDelayMs()))
                        .filter(this::isTransient));
    }

    /** 判定可重试的瞬态故障。网络态（DNS/连接/超时）始终重试；HTTP 状态码错误仅 always 模式下按码重试。 */
    private boolean isTransient(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof ResourceAccessException) {
                return true;
            }
            if (cur instanceof SocketTimeoutException
                    || cur instanceof ConnectException
                    || cur instanceof UnresolvedAddressException) {
                return true;
            }
            if (cur instanceof IOException) {
                return true;
            }
            if (cur instanceof RestClientResponseException rcre) {
                if (props.isAlwaysMode()) {
                    return props.getRetryableCodes().contains(rcre.getStatusCode().value());
                }
                return false;
            }
        }
        return false;
    }

    /** 照搬 llm-retry.localDelay 的指数退避 + 抖动（用 double 规避 long 溢出）。 */
    private long computeDelayMills(int retry) {
        double exponent = Math.min(retry - 1, 30);
        double exponential = Math.min(props.getInitialDelayMs() * Math.pow(2.0, exponent), props.getMaxDelayMs());
        double jitter = 1.0 - props.getJitterRatio() + 2.0 * props.getJitterRatio() * ThreadLocalRandom.current().nextDouble();
        return (long) Math.min(exponential * jitter, props.getMaxDelayMs());
    }

    private static void sleepUninterruptibly(long delayMs) {
        try {
            Thread.sleep(Math.max(1, delayMs));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }
}