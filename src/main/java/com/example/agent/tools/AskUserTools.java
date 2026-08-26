package com.example.agent.tools;

import com.example.agent.config.RunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 显式提问工具：当 agent 遇到无法自动完成的障碍（如图形验证码、需要人工确认）
 * 时调用此工具，向控制台推送提问事件并阻塞等待用户回复。
 * <p>灵感来自 deepseek-harness 的 ask_user_question：把"提问"变成模型可调用的工具，
 * 而非盲目重试或编造。
 */
@Component
public class AskUserTools {

    private static final Logger log = LoggerFactory.getLogger(AskUserTools.class);

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;

    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    @Tool(description = "向用户提问并阻塞等待回复。当遇到无法自动完成的障碍（如图形验证码、需要人工确认操作、缺少凭据）时调用此工具。"
            + "提问会推送到控制台，用户回复后结果返回给你。不要在能自己解决的情况下调用它。")
    public String askUser(@ToolParam(description = "向用户提出的问题，如 'U9登录页有图形验证码，请输入验证码并登录后告诉我' 或 '需要确认是否执行此操作'") String question) {
        String questionId = UUID.randomUUID().toString().replace("-", "");
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(questionId, future);

        RunContext run = RunContext.current();
        if (run != null) {
            run.emit("tool:ask-user", Map.of(
                    "questionId", questionId,
                    "question", question,
                    "runId", run.getRunId()));
        }

        try {
            String answer = future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return "用户回复: " + answer;
        } catch (TimeoutException te) {
            return "提问超时（等待 " + DEFAULT_TIMEOUT_SECONDS + " 秒无回复）。请基于当前信息给出结论，或尝试其他方案。";
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return "提问被中断。";
        } catch (Exception e) {
            return "提问异常: " + e.getMessage();
        } finally {
            pending.remove(questionId);
        }
    }

    /** 前端提交用户对某次提问的回复。返回 false 表示该提问不存在或已超时。 */
    public boolean answer(String questionId, String answer) {
        CompletableFuture<String> future = pending.get(questionId);
        if (future == null || future.isDone()) {
            return false;
        }
        return future.complete(answer);
    }
}
