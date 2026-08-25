package com.example.agent.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件上下文存储：上传文件解析后按 ID 存入内存，
 * 供 ManagerAgent 将文件内容作为上下文分发给各能力域 Agent，而非一股脑塞入 goal。
 */
@Service
public class FileContextService {

    private static final int MAX_ENTRIES = 20;

    private final Map<String, FileContext> contexts = new ConcurrentHashMap<>();

    public String store(String fileName, String content, String extension, long fileSize) {
        String id = "fc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        contexts.put(id, new FileContext(fileName, content, extension, fileSize));
        if (contexts.size() > MAX_ENTRIES) {
            String oldest = contexts.keySet().iterator().next();
            contexts.remove(oldest);
        }
        return id;
    }

    public FileContext get(String id) {
        return contexts.get(id);
    }

    public void remove(String id) {
        contexts.remove(id);
    }

    public record FileContext(String fileName, String content, String extension, long fileSize) {
    }
}
