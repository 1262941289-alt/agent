package com.example.agent.llm;

import com.example.agent.config.AccountPoolProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 账号池 ChatModel：持有多个由 {@link RetryingChatModel} 包装的 provider 模型，
 * 在调用时挑选一个健康账号；失败则按策略降级到下一个账号，并对连续失败的账号做熔断冷却。
 * <p>它是 {@code @Primary ChatModel}，故 {@code AgentConfig} 里所有 ChatClient 自动走账号池。
 * <p>每个账号内部仍受 provider 级重试（指数退避）保护：单账号网络瞬断先由 RetryingChatModel 兜住，
 * 只有当单账号重试耗尽仍失败时，才在本层触发「切账号」降级。
 */
public class PooledChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(PooledChatModel.class);

    /** 单账号句柄：包装后的 ChatModel + 熔断状态。 */
    public static final class AccountHandle {
        public final String name;
        public final ChatModel model;
        /** 连续失败次数。 */
        final AtomicInteger failures = new AtomicInteger(0);
        /** 冷却截止时刻（epoch millis），0 表示未熔断。 */
        final AtomicLong cooldownUntil = new AtomicLong(0);

        public AccountHandle(String name, ChatModel model) {
            this.name = name;
            this.model = model;
        }
    }

    private final List<AccountHandle> accounts;
    private final AccountPoolProperties props;
    private final AtomicInteger roundRobinCursor = new AtomicInteger(0);

    public PooledChatModel(List<AccountHandle> accounts, AccountPoolProperties props) {
        this.accounts = accounts;
        this.props = props;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        int n = accounts.size();
        // 主备模式从首选开始；轮询模式从游标开始。最多尝试 n 个账号。
        int start = props.isRoundRobin()
                ? Math.floorMod(roundRobinCursor.getAndIncrement(), n)
                : 0;

        RuntimeException last = null;
        for (int step = 0; step < n; step++) {
            AccountHandle h = accounts.get((start + step) % n);
            if (isCooling(h)) {
                continue;
            }
            try {
                ChatResponse resp = h.model.call(prompt);
                onSuccess(h);
                return resp;
            } catch (RuntimeException ex) {
                last = ex;
                onFailure(h, ex);
            }
        }
        // 所有账号都失败（或都在冷却），抛最后一个异常；若无异常说明全部在冷却。
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("LLM 账号池所有账号均在熔断冷却中");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 流式不做多账号接力（会截断 token 流）；同样按策略选一个健康账号。
        int n = accounts.size();
        int start = props.isRoundRobin()
                ? Math.floorMod(roundRobinCursor.getAndIncrement(), n)
                : 0;
        for (int step = 0; step < n; step++) {
            AccountHandle h = accounts.get((start + step) % n);
            if (isCooling(h)) {
                continue;
            }
            return h.model.stream(prompt);
        }
        return Flux.error(new IllegalStateException("LLM 账号池所有账号均在熔断冷却中"));
    }

    private boolean isCooling(AccountHandle h) {
        long until = h.cooldownUntil.get();
        return until != 0 && until > System.currentTimeMillis();
    }

    private void onSuccess(AccountHandle h) {
        h.failures.set(0);
        h.cooldownUntil.set(0);
    }

    private void onFailure(AccountHandle h, RuntimeException ex) {
        int f = h.failures.incrementAndGet();
        if (f >= props.getMaxConsecutiveFailures()) {
            h.failures.set(0);
            h.cooldownUntil.set(System.currentTimeMillis() + props.getCooldownMs());
            log.warn("[llm-pool] 账号 {} 连续失败 {} 次，熔断 {}ms（{}）",
                    h.name, f, props.getCooldownMs(), rootMessage(ex));
        } else {
            log.warn("[llm-pool] 账号 {} 第 {} 次失败，切换下一账号（{}）", h.name, f, rootMessage(ex));
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