package com.example.agent.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查端点：供看门狗 watch-agent.ps1 使用，比端口检查更可靠。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("timestamp", System.currentTimeMillis());
        m.put("threadCount", Thread.activeCount());
        Runtime rt = Runtime.getRuntime();
        m.put("usedMemoryMB", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        m.put("maxMemoryMB", rt.maxMemory() / 1024 / 1024);
        return m;
    }
}
