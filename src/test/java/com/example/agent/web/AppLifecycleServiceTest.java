package com.example.agent.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AppLifecycleService 单元测试：状态语义与幂等「启动」语义。
 * <p>关键设计约束（谨慎性）：HTTP 服务无法自举启动已停止的自身——能响应本接口即证明应用在运行，
 * 因此 start() 的语义是「确保运行（幂等）」，绝不做杀死自身/拉起新进程等危险操作。
 */
class AppLifecycleServiceTest {

    private final AppLifecycleService service = new AppLifecycleService(8080);

    @Test
    void statusReportsRunningWithPositivePid() {
        Map<String, Object> s = service.status();
        assertTrue((Boolean) s.get("running"), "能调用本方法即证明应用在运行");
        assertTrue((Long) s.get("pid") > 0, "PID 必须为正数");
    }

    @Test
    void statusEchoesPortAndNonNegativeUptime() {
        Map<String, Object> s = service.status();
        assertEquals(8080, s.get("port"));
        assertTrue((Long) s.get("uptimeMs") >= 0);
        assertNotNull(s.get("startedAt"), "启动时间不能为空");
    }

    @Test
    void startIsIdempotentAndMarksAlreadyRunning() {
        Map<String, Object> r = service.start();
        assertTrue((Boolean) r.get("running"));
        assertTrue((Boolean) r.get("alreadyRunning"), "响应即运行，必须幂等返回 alreadyRunning");
    }

    @Test
    void startPidConsistentWithStatus() {
        assertEquals(service.status().get("pid"), service.start().get("pid"));
    }

    @Test
    void startMessageGuidesColdStart() {
        String msg = (String) service.start().get("message");
        assertTrue(msg.contains("start-agent.bat"),
                "应用停止时本接口不可达，必须指引冷启动方式 start-agent.bat");
    }
}
