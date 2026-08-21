package com.example.agent.tools;

import com.example.agent.browser.BrowserService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 浏览器工具：暴露给 Agent（BrowserWorker）的 @Tool 方法。
 * <p>能力覆盖：打开网页、抓取页面文本/链接/表单结构、点击、填写、按键、截图。
 */
@Component
public class BrowserTools {

    private final BrowserService browser;

    public BrowserTools(BrowserService browser) {
        this.browser = browser;
    }

    @Tool(description = "打开指定 URL 并返回页面摘要（当前地址、标题、正文前若干字）")
    public String navigate(@ToolParam(description = "目标网页完整 URL，如 https://github.com") String url) {
        return browser.navigate(url);
    }

    @Tool(description = "获取当前页面 body 正文文本用于抓取数据；maxChars 控制截断长度")
    public String pageText(@ToolParam(description = "最多返回字符数，默认 8000，0 表示不截断") Integer maxChars) {
        int m = maxChars == null ? 8000 : maxChars;
        return browser.bodyText(m);
    }

    @Tool(description = "按 CSS 选择器提取元素文本，用于抓取表格/列表/卡片等具体内容")
    public String getText(@ToolParam(description = "CSS 选择器，如 'table'、'.repo'、'#name'") String selector) {
        return browser.textOf(selector);
    }

    @Tool(description = "列出页面所有链接（文本 => href，去重限 200 条），用于发现可点击入口")
    public String listLinks() {
        return browser.links();
    }

    @Tool(description = "点击匹配 CSS 选择器的第一个元素，并返回操作后的页面摘要")
    public String click(@ToolParam(description = "CSS 选择器") String selector) {
        return browser.click(selector);
    }

    @Tool(description = "向匹配 CSS 选择器的输入框/文本域填写文本")
    public String fill(@ToolParam(description = "CSS 选择器") String selector,
                       @ToolParam(description = "要填写的文本") String value) {
        return browser.fill(selector, value);
    }

    @Tool(description = "对元素或整个页面按下一个键（如 Enter、Tab、Escape）")
    public String pressKey(@ToolParam(description = "CSS 选择器，留空则作用于整个页面") String selector,
                           @ToolParam(description = "按键名，如 Enter") String key) {
        return browser.pressKey(selector, key);
    }

    @Tool(description = "枚举页面可交互表单字段（input/textarea/select/button），用于了解如何填写并提交")
    public String inspectForm() {
        return browser.formFields();
    }

    @Tool(description = "返回当前页面 URL")
    public String currentUrl() {
        return browser.currentUrl();
    }

    @Tool(description = "对当前页面截图并保存到本地文件")
    public String screenshot(@ToolParam(description = "保存路径，如 target/shot.png") String filePath) {
        return browser.screenshot(filePath);
    }
}