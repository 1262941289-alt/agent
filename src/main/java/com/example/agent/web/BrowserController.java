package com.example.agent.web;

import com.example.agent.browser.BrowserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 浏览器手动/自测入口：直接驱动 {@link BrowserService}，用于验证 Playwright 运行时与调试。
 * <p>正式业务流程由 Agent（BrowserWorker）通过工具调用驱动，不走这里。
 */
@RestController
@RequestMapping("/api/browser")
public class BrowserController {

    private final BrowserService browser;

    public BrowserController(BrowserService browser) {
        this.browser = browser;
    }

    @PostMapping("/navigate")
    public Map<String, Object> navigate(@RequestBody Map<String, String> body) {
        return resp(browser.navigate(body.getOrDefault("url", "")));
    }

    @GetMapping("/text")
    public Map<String, Object> text(@RequestParam(defaultValue = "8000") int maxChars) {
        return resp(browser.bodyText(maxChars));
    }

    @GetMapping("/links")
    public Map<String, Object> links() {
        return resp(browser.links());
    }

    @PostMapping("/click")
    public Map<String, Object> click(@RequestBody Map<String, String> body) {
        return resp(browser.click(body.getOrDefault("selector", "")));
    }

    @PostMapping("/fill")
    public Map<String, Object> fill(@RequestBody Map<String, String> body) {
        return resp(browser.fill(
                body.getOrDefault("selector", ""), body.getOrDefault("value", "")));
    }

    @PostMapping("/press")
    public Map<String, Object> press(@RequestBody Map<String, String> body) {
        return resp(browser.pressKey(
                body.getOrDefault("selector", ""), body.getOrDefault("key", "Enter")));
    }

    @GetMapping("/form")
    public Map<String, Object> form() {
        return resp(browser.formFields());
    }

    @GetMapping("/current-url")
    public Map<String, Object> currentUrl() {
        return resp(browser.currentUrl());
    }

    @GetMapping("/screenshot")
    public Map<String, Object> screenshot(@RequestParam(defaultValue = "target/shot.png") String path) {
        return resp(browser.screenshot(path));
    }

    private Map<String, Object> resp(String text) {
        return Map.of("ok", true, "text", text == null ? "" : text);
    }
}