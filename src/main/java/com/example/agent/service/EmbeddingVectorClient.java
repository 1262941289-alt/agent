package com.example.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Python 向量化推理边车客户端（阶段四）。
 * <p>向 vector_service.py 发起 /similar 请求，返回 query 与各候选文本的余弦相似度；
 * 服务未启用、未启动或出错时返回 null（fail-open），由调用方 {@link com.example.agent.agent.ExperienceRetriever}
 * 回退到字符 bigram 打分，保证经验召回在任何情况下都可用。
 */
@Component
public class EmbeddingVectorClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingVectorClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newHttpClient();

    public EmbeddingVectorClient(@Value("${agent.vector.enabled:true}") boolean enabled,
                                 @Value("${agent.vector.base-url:http://127.0.0.1:8000}") String baseUrl,
                                 @Value("${agent.vector.timeout-ms:8000}") long timeoutMs,
                                 ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(Math.max(1, timeoutMs));
        this.objectMapper = objectMapper;
    }

    /**
     * @return 长度 = candidates.size() 的余弦相似度数组；不可用时返回 null。
     */
    public double[] similarity(String query, List<String> candidates) {
        if (!enabled || candidates == null || candidates.isEmpty()) {
            return null;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("query", query == null ? "" : query);
            ArrayNode arr = body.putArray("candidates");
            for (String c : candidates) {
                arr.add(c == null ? "" : c);
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/similar"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.debug("向量服务返回非 200：{}，回退 bigram", resp.statusCode());
                return null;
            }
            JsonNode node = objectMapper.readTree(resp.body());
            JsonNode scores = node == null ? null : node.get("scores");
            if (scores == null || !scores.isArray()) {
                return null;
            }
            double[] out = new double[Math.min(scores.size(), candidates.size())];
            for (int i = 0; i < out.length; i++) {
                double v = scores.get(i).asDouble();
                out[i] = Double.isNaN(v) ? 0.0 : v;
            }
            return out;
        } catch (Exception e) {
            log.debug("向量服务不可用，回退 bigram：{}", e.getMessage());
            return null;
        }
    }
}