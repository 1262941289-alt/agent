package com.example.agent.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP 超时配置：给 LLM 调用（及所有走自动装配 RestClient 的出站请求）设置
 * 连接 10s / 读取 180s 超时，防止上游（DeepSeek 等）挂起导致 run 无限阻塞。
 * 180s 读取上限覆盖长上下文规划/汇总调用；向量服务自身另有 8s 请求级超时与 fail-open。
 */
@Configuration
public class HttpTimeoutConfig {

    @Bean
    public RestClientCustomizer restClientTimeoutCustomizer() {
        return builder -> {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
            factory.setReadTimeout(Duration.ofSeconds(180));
            builder.requestFactory(factory);
        };
    }
}
