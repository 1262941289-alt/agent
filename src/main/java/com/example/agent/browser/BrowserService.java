package com.example.agent.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 浏览器自动化服务：内嵌 Playwright，管理持久化浏览器实例与页面。
 * <p>默认通过系统已安装的 Edge（channel=msedge）启动，避免下载 Chromium；
 * 使用持久化上下文（用户数据目录）以保留登录态（如 GitHub / 用友 U9 登录）。
 * <p>所有对外方法 synchronized，保证单线程操纵 Page，规避并发安全。
 * <p>企业系统（如用友 U9 ERP）的业务表单嵌在 iframe 内且数据走 XHR 接口：
 * DOM 提取/点击/填写均遍历全部同源 frame；另提供网络抓包（响应报文捕获）能力。
 */
@Service
public class BrowserService {

    private static final Logger log = LoggerFactory.getLogger(BrowserService.class);

    @Value("${sk-agent.browser.headless:false}")
    private boolean headless;

    @Value("${sk-agent.browser.channel:msedge}")
    private String channel;

    @Value("${sk-agent.browser.profile-dir:${user.dir}/target/playwright-profile}")
    private String profileDir;

    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    /** 网络抓包：响应监听缓存（Playwright 回调线程写入，主线程读取）。 */
    private final List<Response> capturedResponses = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean capturing = false;
    private volatile String capturePatterns = "";
    private volatile String captureContentTypes = "";

    /** 打开 URL 并返回页面摘要。 */
    public synchronized String navigate(String url) {
        ensureStarted();
        try {
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        } catch (Exception e) {
            return "导航失败: " + url + "，原因: " + e.getMessage();
        }
        return summary();
    }

    /** 当前页面摘要：地址 + 标题 + 正文前若干字（含全部 iframe 正文）。 */
    public synchronized String summary() {
        ensureStarted();
        return "URL: " + safe(() -> page.url(), "") + "\n标题: " + safe(() -> page.title(), "")
                + "\n页面文本(前3000字):\n" + bodyText(3000);
    }

    /** 正文文本（跨全部 frame 聚合），maxChars<=0 表示不截断。 */
    public synchronized String bodyText(int maxChars) {
        ensureStarted();
        StringBuilder sb = new StringBuilder();
        for (Frame f : page.frames()) {
            try {
                Object r = f.evaluate(
                        "m => { const t = document.body ? document.body.innerText : ''; return m > 0 ? t.slice(0, m) : t; }",
                        maxChars);
                if (r != null && !r.toString().isBlank()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(r.toString());
                }
            } catch (Exception ignored) {
                // 跨域或未加载完成的 frame
            }
        }
        String s = sb.toString().replaceAll("[ \\t]+\\n", "\n").strip();
        return (maxChars > 0 && s.length() > maxChars) ? s.substring(0, maxChars) : s;
    }

    /** 按 CSS 选择器提取文本（遍历全部 frame，去空、限 50 条/帧）。 */
    public synchronized String textOf(String selector) {
        ensureStarted();
        StringBuilder sb = new StringBuilder();
        for (Frame f : page.frames()) {
            try {
                Object r = f.evaluate(
                        "sel => Array.from(document.querySelectorAll(sel)).slice(0, 50)"
                                + ".map(e => (e.innerText || e.value || '').trim()).filter(s => s.length > 0)",
                        selector);
                appendList(sb, r);
            } catch (Exception ignored) {
            }
        }
        String out = sb.toString().strip();
        return out.isBlank() ? "未匹配到任何元素: " + selector : out;
    }

    /** 页面所有链接（文本 => href，去重限 200 条）。 */
    public synchronized String links() {
        ensureStarted();
        Object r = page.evaluate(
                "() => Array.from(new Set(Array.from(document.querySelectorAll('a'))"
                        + ".map(a => (a.innerText || a.title || '').trim().replace(/\\s+/g,' ') + ' => ' + (a.href || ''))"
                        + ".filter(s => s.length > 3))).slice(0, 200)");
        return joinLines(r, "页面无链接");
    }

    /** 点击匹配第一个元素（遍历全部 frame，iframe 内元素也可点），并返回操作后页面摘要。 */
    public synchronized String click(String selector) {
        ensureStarted();
        String lastError = "";
        for (Frame f : page.frames()) {
            try {
                Locator loc = f.locator(selector).first();
                if (loc.count() > 0) {
                    loc.click();
                    quietWait();
                    return "已点击(帧 " + frameLabel(f) + "): " + selector + "\n" + summary();
                }
            } catch (Exception e) {
                lastError = e.getMessage();
            }
        }
        return "点击失败: 所有帧中均未找到可点击元素 " + selector
                + (lastError.isBlank() ? "" : "，原因: " + lastError);
    }

    /** 向匹配输入框/文本域填写文本（遍历全部 frame）。 */
    public synchronized String fill(String selector, String value) {
        ensureStarted();
        for (Frame f : page.frames()) {
            try {
                Locator loc = f.locator(selector).first();
                if (loc.count() > 0) {
                    loc.fill(value);
                    return "已填写(帧 " + frameLabel(f) + "): " + selector + " = " + value;
                }
            } catch (Exception ignored) {
            }
        }
        return "填写失败: 所有帧中均未找到输入元素 " + selector;
    }

    /** 对元素或整个页面按键。 */
    public synchronized String pressKey(String selector, String key) {
        ensureStarted();
        boolean pageLevel = selector == null || selector.isBlank();
        try {
            if (pageLevel) {
                page.keyboard().press(key);
            } else {
                page.locator(selector).first().press(key);
            }
            quietWait();
            return "已按键: " + key + (pageLevel ? "（页面）" : "（" + selector + "）");
        } catch (Exception e) {
            return "按键失败: " + key + "，原因: " + e.getMessage();
        }
    }

    /** 枚举可交互表单字段（遍历全部 frame），帮助模型了解如何填写/提交。 */
    public synchronized String formFields() {
        ensureStarted();
        StringBuilder sb = new StringBuilder();
        for (Frame f : page.frames()) {
            try {
                Object r = f.evaluate(
                        "() => { const out = []; document.querySelectorAll('input, textarea, select, button')"
                                + ".forEach((e) => { const t = e.tagName.toLowerCase();"
                                + " out.push(t + ' id=' + (e.id||'') + ' name=' + (e.name||'') + ' type=' + (e.type||'')"
                                + " + ' placeholder=' + (e.placeholder||'') + ' value=' + (e.value||'')"
                                + " + ' text=' + (e.innerText||'').trim().replace(/\\s+/g,' ')); });"
                                + " return out.slice(0, 100); }");
                appendList(sb, r);
            } catch (Exception ignored) {
            }
        }
        String out = sb.toString().strip();
        return out.isBlank() ? "页面无可交互表单字段" : out;
    }

    public synchronized String currentUrl() {
        ensureStarted();
        return safe(() -> page.url(), "");
    }

    /** 截图保存到本地。 */
    public synchronized String screenshot(String filePath) {
        ensureStarted();
        try {
            page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(filePath)).setFullPage(false));
            return "已截图保存到: " + filePath;
        } catch (Exception e) {
            return "截图失败: " + e.getMessage();
        }
    }

    // ==================== 网络抓包（开发者工具模式） ====================

    /** 开启网络响应抓包。
     * @param urlPatterns  URL 过滤关键词，多个用逗号分隔（命中任意一个即记录），如 "Controller,ReportJson"；空记录全部。
     * @param contentTypes Content-Type 过滤，多个用逗号分隔（包含匹配），如 "json,x-javascript"；空不过滤。 */
    public synchronized String startNetworkCapture(String urlPatterns, String contentTypes) {
        ensureStarted();
        synchronized (capturedResponses) {
            capturedResponses.clear();
        }
        capturePatterns = urlPatterns == null ? "" : urlPatterns.trim();
        captureContentTypes = contentTypes == null ? "" : contentTypes.trim().toLowerCase();
        capturing = true;
        String urlDesc = capturePatterns.isBlank() ? "记录全部数据请求" : "URL 含 '" + capturePatterns + "'";
        String ctDesc = captureContentTypes.isBlank() ? "" : "，且 Content-Type 含 '" + captureContentTypes + "'";
        return "抓包已开启（" + urlDesc + ctDesc + "）。接下来执行 navigate / click 等操作触发数据加载，完成后调用 stopNetworkCapture 读取报文。";
    }

    /** 停止抓包并返回捕获的响应（方法/状态/Content-Type/URL/报文正文，每条报文最多 5000 字）。 */
    public synchronized String stopNetworkCapture(int maxEntries) {
        capturing = false;
        Response[] snapshot;
        synchronized (capturedResponses) {
            snapshot = capturedResponses.toArray(new Response[0]);
        }
        int limit = maxEntries <= 0 ? 30 : Math.min(maxEntries, 100);
        StringBuilder sb = new StringBuilder("抓包结束，共捕获 " + snapshot.length + " 条响应");
        if (snapshot.length > limit) {
            sb.append("（仅显示前 ").append(limit).append(" 条）");
        }
        sb.append("\n");
        for (int i = 0; i < Math.min(snapshot.length, limit); i++) {
            Response r = snapshot[i];
            String method = safe(() -> r.request().method(), "");
            int status = safeInt(() -> r.status(), 0);
            String ct = safe(() -> {
                String h = r.headerValue("content-type");
                return h == null ? "" : h;
            }, "");
            sb.append("\n[").append(i).append("] ").append(method).append(" ").append(status).append(" ").append(ct)
                    .append("\nURL: ").append(safe(() -> r.url(), "")).append("\n");
            if (isTextual(ct)) {
                String body = safe(() -> {
                    byte[] b = r.body();
                    if (b == null) {
                        return "";
                    }
                    int n = Math.min(b.length, 300_000);
                    return new String(b, 0, n, StandardCharsets.UTF_8);
                }, "");
                if (body != null && !body.isBlank()) {
                    String t = body.strip();
                    sb.append("报文(").append(t.length()).append("字): ")
                            .append(t, 0, Math.min(t.length(), 5000)).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private static boolean isTextual(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        return ct.contains("json") || ct.contains("text") || ct.contains("javascript")
                || ct.contains("xml") || ct.contains("form");
    }

    private static boolean isStaticResource(String url) {
        String u = url == null ? "" : url.split("\\?")[0].toLowerCase();
        if (u.endsWith(".js") || u.endsWith(".css") || u.endsWith(".png") || u.endsWith(".jpg")
                || u.endsWith(".jpeg") || u.endsWith(".gif") || u.endsWith(".ico") || u.endsWith(".svg")
                || u.endsWith(".woff") || u.endsWith(".woff2") || u.endsWith(".ttf") || u.endsWith(".axd")) {
            return true;
        }
        return u.contains("/bundles/") || u.contains("/fonts/");
    }

    // ==================== 内部工具 ====================

    private void appendList(StringBuilder sb, Object r) {
        if (r instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    sb.append(o).append("\n");
                }
            }
        } else if (r != null && !r.toString().isBlank()) {
            sb.append(r).append("\n");
        }
    }

    private String joinLines(Object r, String emptyMsg) {
        if (r instanceof List<?> list) {
            List<String> lines = list.stream().map(String::valueOf).toList();
            return lines.isEmpty() ? emptyMsg : String.join("\n", lines);
        }
        String s = r == null ? "" : r.toString();
        return s.isBlank() ? emptyMsg : s;
    }

    private static String frameLabel(Frame f) {
        try {
            String name = f.name() == null || f.name().isBlank() ? "main" : f.name();
            String url = f.url();
            return name + (url == null || url.isBlank() || "about:blank".equals(url) ? "" : "@" + url.split("\\?")[0]);
        } catch (Exception e) {
            return "frame";
        }
    }

    private synchronized void ensureStarted() {
        if (playwright != null) {
            return;
        }
        log.info("启动 Playwright: channel={}, headless={}, profile={}", channel, headless, profileDir);
        playwright = Playwright.create();
        BrowserType.LaunchPersistentContextOptions options =
                new BrowserType.LaunchPersistentContextOptions()
                        .setChannel(channel)
                        .setHeadless(headless)
                        .setViewportSize(1280, 900);
        context = playwright.chromium().launchPersistentContext(Path.of(profileDir), options);
        page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        page.setDefaultTimeout(20000);
        page.setDefaultNavigationTimeout(30000);
        page.onResponse(response -> {
            try {
                if (!capturing || capturedResponses.size() >= 200) {
                    return;
                }
                String url = response.url();
                if (!capturePatterns.isBlank()) {
                    String lower = url.toLowerCase();
                    boolean hit = false;
                    for (String p : capturePatterns.split(",")) {
                        String k = p.trim().toLowerCase();
                        if (!k.isEmpty() && lower.contains(k)) {
                            hit = true;
                            break;
                        }
                    }
                    if (!hit) {
                        return;
                    }
                } else if (isStaticResource(url)) {
                    return;
                }
                if (!captureContentTypes.isBlank()) {
                    String ct = safe(() -> {
                        String h = response.headerValue("content-type");
                        return h == null ? "" : h;
                    }, "").toLowerCase();
                    boolean hit = false;
                    for (String t : captureContentTypes.split(",")) {
                        String k = t.trim();
                        if (!k.isEmpty() && ct.contains(k)) {
                            hit = true;
                            break;
                        }
                    }
                    if (!hit) {
                        return;
                    }
                }
                capturedResponses.add(response);
            } catch (Exception ignored) {
                // 回调里不做任何阻塞调用，失败仅丢弃该条
            }
        });
    }

    private void quietWait() {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        } catch (Exception ignored) {
            // 无导航的点击等场景会超时，忽略
        }
    }

    private interface ValueSupplier {
        Object get() throws Exception;
    }

    private interface IntSupplier {
        int get() throws Exception;
    }

    private static String safe(ValueSupplier s, String def) {
        try {
            Object v = s.get();
            return v == null ? def : v.toString();
        } catch (Exception e) {
            return def;
        }
    }

    private static int safeInt(IntSupplier s, int def) {
        try {
            return s.get();
        } catch (Exception e) {
            return def;
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
            }
            playwright = null;
            context = null;
            page = null;
        }
    }
}
