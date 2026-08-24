package com.example.agent.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后端应用生命周期接口（骨架，TDD RED 阶段）。
 */
@RestController
@RequestMapping("/api/app")
public class AppLifecycleController {

    private final AppLifecycleService appLifecycleService;

    public AppLifecycleController(AppLifecycleService appLifecycleService) {
        this.appLifecycleService = appLifecycleService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return appLifecycleService.status();
    }

    @PostMapping("/start")
    public Map<String, Object> start() {
        return appLifecycleService.start();
    }
}
