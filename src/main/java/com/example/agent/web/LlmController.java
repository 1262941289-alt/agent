package com.example.agent.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 连接检测接口：真实发起一次轻量补全，验证模型可用性与时延。
 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private static final Logger log = LoggerFactory.getLogger(LlmController.class);

    private final ChatClient chatClient;
    private final String model;
    private final String baseUrl;

    public LlmController(@Qualifier("planningChatClient") ChatClient chatClient,
                         @Value("${spring.ai.openai.chat.options.model:unknown}") String model,
                         @Value("${spring.ai.openai.base-url:unknown}") String baseUrl) {
        this.chatClient = chatClient;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    /**
     * 发起一次真实的最小 LLM 调用验证连通性。
     * 返回：connected / model / baseUrl / latencyMs / reply；失败时 connected=false 且带错误原因。
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("model", model);
        out.put("baseUrl", baseUrl);
        long start = System.currentTimeMillis();
        try {
            String reply = chatClient.prompt()
                    .user("连接测试，请只回复两个字符：OK")
                    .call()
                    .content();
            long latency = System.currentTimeMillis() - start;
            out.put("connected", true);
            out.put("latencyMs", latency);
            out.put("reply", reply == null ? "" : reply.trim());
            log.info("LLM 连接检测成功 model={} latency={}ms", model, latency);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            out.put("connected", false);
            out.put("latencyMs", latency);
            out.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.warn("LLM 连接检测失败 model={} latency={}ms err={}", model, latency, String.valueOf(e.getMessage()));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(out);
        }
    }
}
