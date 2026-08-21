package com.example.agent.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 浏览器自动化服务：内嵌 Playwright，管理持久化浏览器实例与页面。
 * <p>默认通过系统已安装的 Edge（channel=msedge）启动，避免下载 Chromium；
 * 使用持久化上下文（用户数据目录）以保留登录态（如 GitHub 登录）。
 * <p>所有对外方法 synchronized，保证单线程操纵 Page，规避并发安全。
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

    /** 当前页面摘要：地址 + 标题 + 正文前若干字。 */
    public synchronized String summary() {
        ensureStarted();
        return "URL: " + safe(() -> page.url(), "") + "\n标题: " + safe(() -> page.title(), "")
                + "\n页面文本(前3000字):\n" + bodyText(3000);
    }

    /** 正文文本，maxChars<=0 表示不截断。 */
    public synchronized String bodyText(int maxChars) {
        ensureStarted();
        Object r = page.evaluate(
                "m => { const t = document.body ? document.body.innerText : ''; return m > 0 ? t.slice(0, m) : t; }",
                maxChars);
        String s = r == null ? "" : r.toString();
        return s.replaceAll("[ \\t]+\\n", "\n").strip();
    }

    /** 按 CSS 选择器提取文本（去空、限 50 条）。 */
    public synchronized String textOf(String selector) {
        ensureStarted();
        Object r = page.evaluate(
                "sel => Array.from(document.querySelectorAll(sel)).slice(0, 50)"
                        + ".map(e => (e.innerText || e.value || '').trim()).filter(s => s.length > 0)",
                selector);
        return joinLines(r, "未匹配到任何元素: " + selector);
    }

    /** 页面所有链接（文本 => href），去重限 200 条。 */
    public synchronized String links() {
        ensureStarted();
        Object r = page.evaluate(
                "() => Array.from(new Set(Array.from(document.querySelectorAll('a'))"
                        + ".map(a => (a.innerText || a.title || '').trim().replace(/\\s+/g,' ') + ' => ' + (a.href || ''))"
                        + ".filter(s => s.length > 3))).slice(0, 200)");
        return joinLines(r, "页面无链接");
    }

    /** 点击匹配第一个元素，并返回操作后页面摘要。 */
    public synchronized String click(String selector) {
        ensureStarted();
        try {
            page.locator(selector).first().click();
            quietWait();
            return "已点击: " + selector + "\n" + summary();
        } catch (Exception e) {
            return "点击失败: " + selector + "，原因: " + e.getMessage();
        }
    }

    /** 向匹配输入框/文本域填写文本。 */
    public synchronized String fill(String selector, String value) {
        ensureStarted();
        try {
            page.locator(selector).first().fill(value);
            return "已填写: " + selector + " = " + value;
        } catch (Exception e) {
            return "填写失败: " + selector + "，原因: " + e.getMessage();
        }
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

    /** 枚举可交互表单字段，帮助模型了解如何填写/提交。 */
    public synchronized String formFields() {
        ensureStarted();
        Object r = page.evaluate(
                "() => { const out = []; document.querySelectorAll('input, textarea, select, button')"
                        + ".forEach((e) => { const t = e.tagName.toLowerCase();"
                        + " out.push(t + ' id=' + (e.id||'') + ' name=' + (e.name||'') + ' type=' + (e.type||'')"
                        + " + ' placeholder=' + (e.placeholder||'') + ' value=' + (e.value||'')"
                        + " + ' text=' + (e.innerText||'').trim().replace(/\\s+/g,' ')); });"
                        + " return out.slice(0, 100); }");
        return joinLines(r, "页面无可交互表单字段");
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

    private String joinLines(Object r, String emptyMsg) {
        if (r instanceof List<?> list) {
            List<String> lines = list.stream().map(String::valueOf).toList();
            return lines.isEmpty() ? emptyMsg : String.join("\n", lines);
        }
        String s = r == null ? "" : r.toString();
        return s.isBlank() ? emptyMsg : s;
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

    private static String safe(ValueSupplier s, String def) {
        try {
            Object v = s.get();
            return v == null ? def : v.toString();
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