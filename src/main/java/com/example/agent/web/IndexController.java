package com.example.agent.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 简单 Web 测试页跳转。
 */
@Controller
public class IndexController {

    @GetMapping("/")
    public String index() {
        return "index.html";
    }
}