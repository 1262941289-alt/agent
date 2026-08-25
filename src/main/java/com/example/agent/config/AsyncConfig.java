package com.example.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行线程池配置：有界线程池 + CallerRunsPolicy 拒绝策略。
 * <p>corePool=8/maxPool=32：LLM 调用是 IO 密集型，可适当开多线程。
 * <p>queue=200：有界队列防 OOM；满后由调用线程执行（backpressure）。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "filterTaskExecutor")
    public ThreadPoolTaskExecutor filterTaskExecutor() {
        return create("filter-task-", 4, 16, 500);
    }

    @Bean(name = "agentExecutor")
    public ThreadPoolTaskExecutor agentExecutor() {
        return create("agent-task-", 8, 32, 200);
    }

    /**
     * 工具守护执行线程池：用于给单次工具调用加超时，避免与 agentExecutor 共用导致借位死锁
     * （agentExecutor 上的 step 子任务等空闲 worker，而 worker 又在等该 step）。
     */
    @Bean(name = "toolExecutor")
    public ThreadPoolTaskExecutor toolExecutor() {
        return create("tool-task-", 4, 16, 1000);
    }

    private ThreadPoolTaskExecutor create(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
