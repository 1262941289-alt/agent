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

    @Tool(description = "按可见文字点击按钮/菜单项/链接（如 clickButton('查找')、clickButton('物料清单')），自动跨 iframe 匹配第一个文本相等或包含的元素。定位工具栏按钮或导航菜单时优先用这个，不要反复 getText 探测")
    public String clickButton(@ToolParam(description = "元素可见文字，如 '查找'、'物料清单'、'确定'") String text) {
        return browser.clickByText(text);
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

    @Tool(description = "【页面探针】扫描当前页面全部 iframe，一次性返回结构化布局骨架：输入框/按钮/链接/下拉/表格表头与前几行/菜单导航。刚打开未知页面、想快速判断数据在哪、从哪个控件入手时优先用它，比逐个 getText 探测更高效")
    public String probePage() {
        return browser.probePage();
    }

    @Tool(description = "用友 U9 自动登录（内置登录状态机，会自动区分有无图形验证码）：用配置/入参的账号密码登录 U9。返回会明确告知四种情形之一：①无验证码→已自动登录；②登录页自带图形验证码→需人工输入；③提交后要求验证码→需人工输入；④已登录→跳过。只有返回里明确说「验证码/人工」时才需要人工介入，不要因登录页有输入框就误判需要验证码")
    public String u9Login(@ToolParam(description = "账号，默认用配置 sk-agent.u9.account，可留空") String account,
                          @ToolParam(description = "密码，默认用配置 sk-agent.u9.password，可留空") String password) {
        return browser.u9Login(account, password);
    }

    @Tool(description = "对当前页面截图并保存到本地文件")
    public String screenshot(@ToolParam(description = "保存路径，如 target/shot.png") String filePath) {
        return browser.screenshot(filePath);
    }

    @Tool(description = "【网络抓包】开启开发者工具式响应捕获：开始记录页面的 XHR/Fetch/POST 网络请求与响应报文。适用于 SPA 或 iframe 内嵌的企业系统（如用友 U9 ERP）直接获取数据接口报文，比解析 DOM 更可靠。用法：先开启抓包 → 执行 navigate/click 触发数据加载 → 再调用 stopNetworkCapture 读取报文")
    public String startNetworkCapture(
            @ToolParam(description = "URL 过滤关键词，多个用逗号分隔（命中任意一个即记录），如 'Controller,ReportJson'；留空记录全部数据请求（自动跳过静态资源）") String urlPatterns,
            @ToolParam(description = "Content-Type 过滤，多个用逗号分隔（包含匹配），如 'json,x-javascript'；留空不过滤。建议配合 urlPatterns 使用以避开 display.aspx 的 HTML 文档") String contentTypes) {
        return browser.startNetworkCapture(urlPatterns, contentTypes);
    }

    @Tool(description = "【网络抓包】停止抓包并返回捕获的响应清单（方法/状态/Content-Type/URL/响应报文正文，每条最多 5000 字）。在触发页面数据加载的操作完成后立即调用")
    public String stopNetworkCapture(@ToolParam(description = "最多返回条数，默认 30") Integer maxEntries) {
        return browser.stopNetworkCapture(maxEntries == null ? 30 : maxEntries);
    }
}