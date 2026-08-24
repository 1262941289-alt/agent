package com.example.agent.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后端应用生命周期服务（骨架，TDD RED 阶段）。
 */
@Service
public class AppLifecycleService {

    private final int port;

    public AppLifecycleService(@Value("${server.port:8080}") int port) {
        this.port = port;
    }

    /**
     * 当前运行状态。能响应本方法即证明应用在运行，running 恒为 true。
     */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", true);
        m.put("pid", currentPid());
        m.put("uptimeMs", currentUptimeMs());
        m.put("startedAt", java.time.Instant.ofEpochMilli(
                java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime()).toString());
        m.put("port", port);
        return m;
    }

    /**
     * 幂等「启动」：能响应本接口即证明应用已在运行。
     * <p>谨慎性约束：HTTP 服务无法自举启动已停止的自身，本方法绝不做杀死进程/拉起新 JVM
     * 等危险操作；应用停止时的冷启动由本机脚本 start-agent.bat 完成，message 中给出指引。
     */
    public Map<String, Object> start() {
        Map<String, Object> m = status();
        m.put("alreadyRunning", true);
        m.put("message", "后端应用已在运行（PID " + currentPid() + "，端口 " + port
                + "）。应用停止时本接口不可达，请在项目根目录运行 start-agent.bat 冷启动。");
        return m;
    }

    protected long currentPid() {
        return ProcessHandle.current().pid();
    }

    protected long currentUptimeMs() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
    }
}
