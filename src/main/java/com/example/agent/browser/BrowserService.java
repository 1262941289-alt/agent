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

    @Value("${sk-agent.u9.account:}")
    private String u9Account;

    @Value("${sk-agent.u9.password:}")
    private String u9Password;

    @Value("${sk-agent.u9.login-url:http://10.225.72.151/U9/mvc/login/index}")
    private String u9LoginUrl;

    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    /** 会话内全部页面（含 U9「查找」等弹窗新开的 popup 窗口）；主页面恒为第一个。 */
    private final List<Page> allPages = new ArrayList<>();

    static final String U9_LOGIN_JS = """
            ([a, p]) => {
              const inputs = Array.from(document.querySelectorAll('input'));
              const skip = e => { const t = e.type || ''; return t === 'hidden' || t === 'checkbox' || t === 'radio' || t === 'button' || t === 'submit' || t === 'file'; };
              const pwdBox = inputs.find(e => e.type === 'password');
              if (!pwdBox) return null;
              const key = s => (s || '').toLowerCase();
              const acctBox = inputs.find(e => !skip(e) && e !== pwdBox && /user|login|account|accountcode|账号|用户名/.test(key(e.name) + ' ' + key(e.id) + ' ' + key(e.placeholder)))
                  || inputs.find(e => !skip(e) && e !== pwdBox && e.type === 'text');
              if (!acctBox) return null;
              const setVal = (el, v) => {
                try { const d = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value'); if (d && d.set) d.set.call(el, v); else el.value = v; }
                catch (e) { el.value = v; }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
              };
              setVal(acctBox, a); setVal(pwdBox, p);
              // U9 登录无强制图形验证码：填好账号密码后直接点【登录】，不因页面存在验证码输入框而停顿。
              const btn = Array.from(document.querySelectorAll('button, input[type=button], input[type=submit], a, span'))
                .find(e => { const t = (e.innerText || e.value || '').trim(); return t && (t.indexOf('登') >= 0); });
              if (btn) { btn.click(); return 'OK 已点击登录按钮: ' + ((btn.innerText || btn.value || '').trim()); }
              return 'OK 已填写账号密码，未找到登录按钮';
            }
            """;
    /** 登录状态机探测脚本：判定当前 frame 属于哪种登录情形（是否有图形验证码图片）。 */
    static final String LOGIN_STATE_JS = """
            () => {
              const inputs = Array.from(document.querySelectorAll('input'));
              const pwd = inputs.find(e => (e.type || '').toLowerCase() === 'password');
              if (!pwd) return 'state=NOT_LOGIN';
              // 登录判定以「图形验证码图片」为准：只有探测到验证码图片才判 CAPTCHA；
              // 页面仅有验证码输入框（无图）不算图形验证码，走 PLAIN 自动登录。
              const inpRe = /captcha|verify|validcode|validatecode|checkcode|yzm|randcode|验证码|校验码/i;
              // 匹配验证码图片：src 非空时 URL 出现 code 等关键词即判为验证码图；
              // src 为空时仅凭 alt/title/id 且必须命中强验证码词（不含裸 code），杜绝空 img 误判。
              const imgSrcRe = /captcha|verify|validcode|validatecode|checkcode|yzm|randcode|验证码|校验码|code/i;
              const imgs = Array.from(document.querySelectorAll('img'));
              const capImg = imgs.find(e => {
                const src = e.src || '';
                const idname = ((e.id || '') + ' ' + (e.name || '') + ' ' + (e.className || '') + ' ' + (e.alt || '') + ' ' + (e.title || ''));
                if (src && imgSrcRe.test(src)) return true;
                if (inpRe.test(idname)) return true;
                // U9 动态验证码图：id/name 形如 codeImage/codeImg/ImageCode，src 可能为空或为动态 base64
                return /code/i.test(idname) && /image|img|captcha|verify|yzm|randcode|validcode|checkcode/i.test(idname);
              });
              if (capImg) return 'state=CAPTCHA|kind=img|src=' + ((capImg.src || '').substring(0, 160));
              return 'state=PLAIN';
            }
            """;
    /** 页面探针：探测当前 frame 的结构化骨架（输入框/按钮/链接/下拉/表格/菜单），供 agent 快速识别页面布局与数据位置。 */
    static final String PROBE_JS = """
            () => {
              const grab = s => Array.from(document.querySelectorAll(s));
              const info = [];
              const t = e => ((e.innerText || e.value || '').trim().replace(/\\s+/g, ' '));
              grab('input').slice(0, 30).forEach(e => info.push('输入框[' + (e.type || 'text') + '] name=' + (e.name || '') + ' id=' + (e.id || '') + ' 占位=' + (e.placeholder || '') + ' 值=' + (e.value || '')));
              grab('textarea').slice(0, 10).forEach(e => info.push('文本域 name=' + (e.name || '') + ' id=' + (e.id || '') + ' 值=' + (e.value || '')));
              grab('select').slice(0, 20).forEach(e => info.push('下拉 name=' + (e.name || '') + ' 选项=' + Array.from(e.options || []).map(o => (o.text || '')).filter(Boolean).join('/')));
              grab('button, input[type=button], input[type=submit]').slice(0, 30).forEach(e => { const x = t(e); if (x) info.push('按钮=' + x); });
              grab('a').slice(0, 40).forEach(e => { const x = t(e); if (x && x.length < 60) info.push('链接 ' + x + ' => ' + (e.href || '')); });
              let ti = 0;
              grab('table').slice(0, 10).forEach(tbl => {
                const head = Array.from((tbl.querySelector('thead') ? tbl.querySelectorAll('thead th') : tbl.querySelectorAll('tr:first-of-type th'))).map(h => (h.innerText || '').trim().replace(/\\s+/g, ' ')).filter(Boolean);
                const body = tbl.tBodies && tbl.tBodies[0] ? tbl.tBodies[0] : tbl;
                const colCount = tbl.rows && tbl.rows[0] ? tbl.rows[0].cells.length : 0;
                info.push('表格#' + ti + ' 列数=' + colCount + (head.length ? ' 表头=[' + head.join(',') + ']' : ''));
                Array.from(body.querySelectorAll('tr')).slice(0, 5).forEach(r => { const cells = Array.from(r.children).map(c => (c.innerText || '').trim().replace(/\\s+/g, ' ')).filter(Boolean).slice(0, 8); if (cells.length) info.push('  行: ' + cells.join(' | ')); });
                ti++;
              });
              grab('nav, [class*=menu], [class*=sidebar], [class*=tree]').slice(0, 10).forEach(m => { const x = (m.innerText || '').trim().replace(/\\s+/g, ' '); if (x && x.length < 800) info.push('菜单/导航: ' + x); });
              return info.slice(0, 120).join('\\n');
            }
            """;
    private volatile boolean capturing = false;
    private volatile String capturePatterns = "";
    private volatile String captureContentTypes = "";

    /** 网络抓包：响应监听缓存（Playwright 回调线程写入，主线程读取）。 */
    private final List<Response> capturedResponses = Collections.synchronizedList(new ArrayList<>());

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

    /** 正文文本（跨全部页面 & frame 聚合），maxChars<=0 表示不截断。 */
    public synchronized String bodyText(int maxChars) {
        ensureStarted();
        StringBuilder sb = new StringBuilder();
        for (Frame f : allFrames()) {
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

    /** 按 CSS 选择器提取文本（遍历全部页面 & frame，去空、限 50 条/帧）。 */
    public synchronized String textOf(String selector) {
        ensureStarted();
        StringBuilder sb = new StringBuilder();
        for (Frame f : allFrames()) {
            try {
                Object r = f.evaluate(
                        "sel => Array.from(document.querySelectorAll(sel)).slice(0, 50)"
                                + ".map(e => { const t = e.tagName?e.tagName.toLowerCase():''; const ty = e.type||'';"
                                + " const nm = e.name||''; const id = e.id||''; const tx = (e.innerText||e.value||'').trim().replace(/\\s+/g,' ');"
                                + " return (t + (ty?'['+ty+']':'') + (nm?' name='+nm:'') + (id?' #'+id:'') + (tx?' => '+tx:'')).trim(); })"
                                + ".filter(s => s.length > 0)",
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

    /** 点击匹配第一个元素（遍历全部页面 & frame，iframe/弹窗内元素也可点），并返回操作后页面摘要。 */
    public synchronized String click(String selector) {
        ensureStarted();
        String lastError = "";
        for (Frame f : allFrames()) {
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

    /** 按可见文本点击按钮/链接/菜单项（遍历全部页面 & frame，匹配第一个文本相等或包含的元素），用于工具栏按钮与导航菜单（如「查找」「物料清单」）。 */
    public synchronized String clickByText(String text) {
        ensureStarted();
        String lastError = "";
        for (Frame f : allFrames()) {
            try {
                Object r = f.evaluate(
                        "t => { const nodes = document.querySelectorAll('button, a, input[type=button], input[type=submit], span, li');"
                                + " const target = Array.from(nodes).find(e => { const x = (e.innerText || e.value || '').trim(); return x && (x === t || x.includes(t)); });"
                                + " if (!target) { return null; } target.click();"
                                + " return target.tagName.toLowerCase() + ' => ' + ((target.innerText || target.value || '').trim()); }",
                        text);
                if (r != null && !r.toString().isBlank()) {
                    quietWait();
                    return "已点击(帧 " + frameLabel(f) + "): " + r;
                }
            } catch (Exception e) {
                lastError = e.getMessage();
            }
        }
        return "点击失败: 未找到可见文本为 '" + text + "' 的可点击元素"
                + (lastError.isBlank() ? "" : "，原因: " + lastError);
    }

    /** 向匹配输入框/文本域填写文本（遍历全部页面 & frame）。 */
    public synchronized String fill(String selector, String value) {
        ensureStarted();
        for (Frame f : allFrames()) {
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

    /** 枚举可交互表单字段（遍历全部页面 & frame），帮助模型了解如何填写/提交。 */
    public synchronized String formFields() {
        ensureStarted();
        StringBuilder sb = new StringBuilder();
        for (Frame f : allFrames()) {
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

    /**
     * 用友 U9 自动登录（状态机驱动）：先探测登录页属于哪种情形（有无图形验证码），再分流处理。
     * <ul>
     *   <li>NOT_LOGIN —— 不在登录页（已登录或业务页），无需登录，直接返回。</li>
     *   <li>CAPTCHA —— 登录页已有图形验证码，不自动提交，返回人工识别提示。</li>
     *   <li>PLAIN —— 标准登录页，填账号密码后点登录；提交后再探测一次，
     *       若服务端此时才下发验证码（条件性验证码），返回 CAPTCHA_AFTER_SUBMIT 提示人工。</li>
     * </ul>
     * 登录态按持久化 profile 保留，下次无需再登。
     */
    public synchronized String u9Login(String account, String password) {
        ensureStarted();
        String acc = (account == null || account.isBlank()) ? u9Account : account;
        String pwd = (password == null || password.isBlank()) ? u9Password : password;
        if (acc.isBlank() || pwd.isBlank()) {
            return "U9 自动登录：账号或密码为空（sk-agent.u9.account / password 未配置）。请人工登录一次以保留登录态，或在 local/secret.env 配置 U9_ACCOUNT / U9_PASSWORD。";
        }
        // 状态机第一步：提交前探测登录页状态（跨全部 frame，含 iframe）。
        // U9 的登录表单嵌在 iframe 内，外层主 page 会因导航失败停在 chrome-error://chromewebdata/，
        // 因此绝不能以 page.url() 是否含 /U9/ 来判断是否在登录页，必须信赖「探测到的登录表单」。
        U9LoginState pre = detectLoginState();
        if (pre == U9LoginState.NOT_LOGIN) {
            // 探测不到登录表单时才尝试导航到登录页再探测；已停在某个 U9 业务 URL 则不重复导航
            if (!safe(() -> page.url(), "").contains("/U9/")) {
                try {
                    page.navigate(u9LoginUrl);
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                } catch (Exception e) {
                    return "U9 自动登录失败：导航到登录页出错，原因: " + e.getMessage();
                }
                pre = detectLoginState();
            }
        }
        if (pre == U9LoginState.NOT_LOGIN) {
            return "U9 自动登录：当前不在登录页（已登录或已是业务页），无需登录。当前 URL: " + safe(() -> page.url(), "");
        }
        if (pre == U9LoginState.CAPTCHA) {
            return "U9 登录检测到图形验证码（" + loginStateDetail() + "）。自动化无法识别图片验证码，请在浏览器窗口人工输入验证码并点击登录；登录态会保留，之后无需再重复。";
        }

        // 状态机第二步：PLAIN，填账号密码并点登录
        for (Frame f : allFrames()) {
            try {
                Object r = f.evaluate(U9_LOGIN_JS, new Object[]{acc, pwd});
                String s = r == null ? null : r.toString();
                if (s == null || s.isBlank()) {
                    continue;
                }
                quietWait();
                if (s.startsWith("OK")) {
                    // 状态机第三步：提交后再探测，区分「登录成功 / 密码错误 / 提交后才要求验证码」
                    U9LoginState post = detectLoginState();
                    if (post == U9LoginState.CAPTCHA) {
                        return "U9 已填写账号密码并点击登录，但提交后系统要求图形验证码（" + loginStateDetail()
                                + "）。请在浏览器窗口人工输入验证码完成登录；登录态会保留。";
                    }
                    if (post == U9LoginState.NOT_LOGIN) {
                        return "U9 自动登录完成：账号 " + acc + " 已离开登录页，登录成功。";
                    }
                    return "U9 已填写账号密码并点击登录（" + s + "）。当前仍在登录页，可能是账号/密码错误或服务端尚未跳转，请人工核对，或稍后调用 currentUrl/probePage 确认。";
                }
            } catch (Exception ignored) {
                // 跨域或不含登录表单的 frame
            }
        }
        return "U9 自动登录失败：未在页面中找到登录表单（账号/密码输入框）。当前 URL: " + safe(() -> page.url(), "");
    }

    /** 状态机探测：遍历全部 frame 执行登录状态脚本，返回当前登录情形。多个 frame 命中有验证码时优先返回 CAPTCHA。 */
    private U9LoginState detectLoginState() {
        U9LoginState best = U9LoginState.NOT_LOGIN;
        for (Frame f : allFrames()) {
            try {
                Object r = f.evaluate(LOGIN_STATE_JS, null);
                String s = r == null ? null : r.toString();
                if (s == null || s.isBlank()) {
                    continue;
                }
                if (s.startsWith("state=CAPTCHA")) {
                    return U9LoginState.CAPTCHA;
                }
                if (s.startsWith("state=PLAIN")) {
                    best = U9LoginState.PLAIN;
                }
            } catch (Exception ignored) {
                // 跨域或不含可解析 DOM 的 frame
            }
        }
        return best;
    }

    /** 读取验证码探测的详情字段（kind/src），用于返回给 agent 的人工提示。 */
    private String loginStateDetail() {
        for (Frame f : allFrames()) {
            try {
                Object r = f.evaluate(LOGIN_STATE_JS, null);
                String s = r == null ? null : r.toString();
                if (s != null && s.startsWith("state=CAPTCHA")) {
                    return s.substring("state=CAPTCHA".length()).replaceFirst("^\\|", "");
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return "未知验证码形态";
    }

    /**
     * 页面探针：扫描全部 frame 的结构化骨架，返回当前页面的可交互元素、表格、菜单/导航，
     * 供 agent 在不解析 DOM 的前提下快速识别页面布局与数据所在位置。
     * <p>比 getText/pageText 更适合「我刚到一个未知页面，想知道从哪入手」的场景：一次性给出输入框/按钮/链接/下拉/表格表头。
     * 会同时扫描 U9 查找等弹窗 popup 页面，便于驱动模态查找框。
     */
    public synchronized String probePage() {
        ensureStarted();
        StringBuilder sb = new StringBuilder("=== 页面探针 ===\n当前URL: ").append(safe(() -> page.url(), "")).append("\n");
        boolean any = false;
        for (Frame f : allFrames()) {
            try {
                Object r = f.evaluate(PROBE_JS, null);
                String s = r == null ? null : r.toString();
                if (s != null && !s.isBlank()) {
                    any = true;
                    sb.append("\n【帧 ").append(frameLabel(f)).append("】\n").append(s).append("\n");
                }
            } catch (Exception ignored) {
                // 跨域或不含可解析 DOM 的 frame
            }
        }
        return any ? sb.toString().strip() : "探针未发现可交互元素或表格（可能页面为纯展示或仍在加载）。";
    }

    public synchronized String currentUrl() {
        ensureStarted();
        return safe(() -> page.url(), "");
    }

    /** 截图保存到本地（优先截取最近打开的页面，含 U9 查找弹窗 popup）。 */
    public synchronized String screenshot(String filePath) {
        ensureStarted();
        try {
            activePage().screenshot(new Page.ScreenshotOptions().setPath(Path.of(filePath)).setFullPage(false));
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
            boolean textual = isTextual(ct);
            String body = safe(() -> {
                byte[] b = r.body();
                if (b == null) {
                    return "";
                }
                int n = Math.min(b.length, 300_000);
                return new String(b, 0, n, StandardCharsets.UTF_8);
            }, "");
            if (body != null) {
                String t = body.strip();
                // JSON 报文嗅探：即便 Content-Type 不是 json（如 text/plain/application/octet-stream），
                // 只要正文以 { 或 [ 开头就仍输出，避免漏掉真实数据接口
                boolean looksJson = t.startsWith("{") || t.startsWith("[");
                if (textual || looksJson) {
                    if (!t.isBlank()) {
                        sb.append("报文(").append(t.length()).append("字): ")
                                .append(t, 0, Math.min(t.length(), 5000)).append("\n");
                    }
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
        if (playwright != null && isHealthy()) {
            return;
        }
        if (playwright != null) {
            // 浏览器进程崩溃/被用户关闭后，Playwright 引用仍在但 page/context 失效（TargetClosedError）。
            // 先彻底清理再重新拉起，避免 agent 在死上下文上空转。
            log.warn("检测到 Playwright 浏览器实例已失效（崩溃或被关闭），自动重建");
            teardownQuietly();
        }
        log.info("启动 Playwright: channel={}, headless={}, profile={}", channel, headless, profileDir);
        playwright = Playwright.create();
        BrowserType.LaunchPersistentContextOptions options =
                new BrowserType.LaunchPersistentContextOptions()
                        .setChannel(channel)
                        .setHeadless(headless)
                        // 容器内以 root 跑无头 chromium，必须禁用沙箱并规避过小的 /dev/shm；本机 Windows 下两参数被忽略，不影响
                        .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage"))
                        .setViewportSize(1280, 900);
        context = playwright.chromium().launchPersistentContext(Path.of(profileDir), options);
        // 追踪会话内所有页面（含 U9 查找等新开的 popup 窗口），并对每个页面挂响应抓包监听
        context.onPage(p -> {
            synchronized (allPages) {
                allPages.add(p);
            }
            attachCapture(p);
            p.onClose(e -> {
                synchronized (allPages) {
                    allPages.remove(p);
                }
            });
        });
        for (Page existing : context.pages()) {
            synchronized (allPages) {
                if (!allPages.contains(existing)) {
                    allPages.add(existing);
                }
            }
            attachCapture(existing);
        }
        page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        if (!allPages.contains(page)) {
            synchronized (allPages) {
                allPages.add(0, page);
            }
        }
        page.setDefaultTimeout(20000);
        page.setDefaultNavigationTimeout(30000);
    }

    /** 全部打开页面的全部 frame（含 iframe 与弹窗 popup），供跨页读取/点击/填写。 */
    private List<Frame> allFrames() {
        List<Frame> frames = new ArrayList<>();
        List<Page> snapshot;
        synchronized (allPages) {
            snapshot = new ArrayList<>(allPages);
        }
        for (Page p : snapshot) {
            try {
                frames.addAll(p.frames());
            } catch (Exception ignored) {
                // 页面可能已关闭
            }
        }
        return frames;
    }

    /** 最近打开的页面（主页面之外的 popup 弹窗优先）；无其他页面时回退主页面。 */
    private Page activePage() {
        List<Page> snapshot;
        synchronized (allPages) {
            snapshot = new ArrayList<>(allPages);
        }
        return snapshot.isEmpty() ? page : snapshot.get(snapshot.size() - 1);
    }

    /** 给指定页面挂网络响应抓包监听（回调线程写入，主线程读取，不做阻塞调用）。 */
    private void attachCapture(Page p) {
        try {
            p.onResponse(response -> {
                try {
                    if (!capturing || capturedResponses.size() >= 200) {
                        return;
                    }
                    String url = response.url();
                    if (!capturePatterns.isBlank()) {
                        String lower = url.toLowerCase();
                        boolean hit = false;
                        for (String pat : capturePatterns.split(",")) {
                            String k = pat.trim().toLowerCase();
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
        } catch (Exception ignored) {
            // 忽略回调注册异常
        }
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

    /** 探测浏览器实例是否仍可用：page/context 为空，或对框架做一次轻量求值时抛异常（崩溃）即视为失效。 */
    private boolean isHealthy() {
        if (context == null || page == null) {
            return false;
        }
        try {
            page.evaluate("() => 1", null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 释放 Playwright 资源并清空引用，供重建或销毁复用。 */
    private void teardownQuietly() {
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
            }
        }
        playwright = null;
        context = null;
        page = null;
        synchronized (allPages) {
            allPages.clear();
        }
    }

    @PreDestroy
    public synchronized void close() {
        teardownQuietly();
    }
}
