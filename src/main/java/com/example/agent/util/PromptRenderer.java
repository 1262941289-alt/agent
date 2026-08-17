package com.example.agent.util;

import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 提示词工具：加载 classpath 下的提示词模板，并渲染 {key} 占位符。
 */
public final class PromptRenderer {

    private PromptRenderer() {
    }

    public static String load(String classpath) {
        try {
            return new ClassPathResource(classpath).getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("无法加载提示词模板: " + classpath, e);
        }
    }

    public static String render(String template, Map<String, String> vars) {
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    /**
     * 从 LLM 文本响应中提取第一个 JSON 对象子串（兼容外层有 ```json 代码块的情况）。
     *
     * @return 提取到的 JSON 对象字符串；未找到返回 null
     */
    public static String extractJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }
}
