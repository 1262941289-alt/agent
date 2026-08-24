package com.example.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AppLifecycleController 契约测试：启动/状态接口的形状与幂等语义。
 */
class AppLifecycleControllerTest {

    private MockMvc mvc() {
        AppLifecycleServiceStub stub = new AppLifecycleServiceStub();
        return MockMvcBuilders.standaloneSetup(new AppLifecycleController(stub)).build();
    }

    @Test
    void getStatusReturns200WithRunningTrue() throws Exception {
        mvc().perform(get("/api/app/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.pid").value(1234))
                .andExpect(jsonPath("$.port").value(8080));
    }

    @Test
    void postStartReturns200AndIsIdempotent() throws Exception {
        mvc().perform(post("/api/app/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.alreadyRunning").value(true))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /** 固定值桩：隔离真实进程信息，只验证控制器层的 JSON 契约。 */
    static class AppLifecycleServiceStub extends com.example.agent.web.AppLifecycleService {
        AppLifecycleServiceStub() {
            super(8080);
        }

        @Override
        protected long currentPid() {
            return 1234L;
        }

        @Override
        protected long currentUptimeMs() {
            return 5000L;
        }
    }
}
