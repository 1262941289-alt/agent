package soma;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AGENT SOMA · 桌面工作台（纯 Java Swing，无第三方依赖）
 * 通过后端 REST / SSE 接口驱动自组织多智能体，提供深色科技感 GUI。
 */
public final class SomaDesktop {

    // ---------------------------------------------------------------- theme
    static final Color BG   = hex("#05070d");
    static final Color BG2  = hex("#080c16");
    static final Color PANEL   = hex("#0f1524");
    static final Color PANEL2  = hex("#121929");
    static final Color LINE  = hex("#1c2537");
    static final Color LINE_S = hex("#27344e");
    static final Color TXT   = hex("#e7eefb");
    static final Color DIM   = hex("#8b98b4");
    static final Color FAINT = hex("#5d6a86");
    static final Color ACC   = hex("#3fe0c5");
    static final Color ACC2  = hex("#56e0f0");
    static final Color VIOLET= hex("#a78bfa");
    static final Color AMBER = hex("#fbbf24");
    static final Color ROSE  = hex("#fb7185");
    static final Color OK    = hex("#34d399");

    static final String BASE = "http://localhost:8080";
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    static String MONO;

    // ------------------------------------------------------------- UI state
    static JFrame frame;
    static JLabel healthDot, healthText, llmDot, llmText, mgrText, backendText;
    static JPanel agentPanel;    // left scroll content
    static JPanel tracePanel;    // center scroll content
    static JScrollPane traceScroll;
    static JTextArea goalArea;
    static JButton runBtn, stopBtn;
    static JLabel runStatus;
    static JPanel approvalPanel; // right approvals list
    static JLabel askLabel;
    static JTextField askField;
    static JButton askSend;
    static String pendingQuestionId;
    static JPanel historyPanel;  // right history list
    static JComboBox<String> fmtCombo;
    static JButton exportBtn;
    static JLabel finalLabel;

    static String currentRunId;
    static String currentGoal = "";
    static String currentFinal = "";
    static String currentTermination = "";
    static volatile boolean running = false;

    static final int MAX_CARDS = 900;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            initTheme();
            build();
            refreshAll();
        });
    }

    // ------------------------------------------------------------- fonts
    static void initTheme() {
        String[] cand = {"JetBrains Mono", "Cascadia Code", "Cascadia Mono", "Consolas", "DejaVu Sans Mono", "Monospaced"};
        String[] avail = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(avail));
        MONO = Font.MONOSPACED;
        for (String c : cand) {
            if (have.contains(c)) { MONO = c; break; }
        }
        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", TXT);
        UIManager.put("TextField.background", PANEL2);
        UIManager.put("TextField.foreground", TXT);
        UIManager.put("TextField.caretForeground", ACC);
        UIManager.put("TextArea.background", PANEL2);
        UIManager.put("TextArea.foreground", TXT);
        UIManager.put("TextArea.caretForeground", ACC);
        UIManager.put("ComboBox.background", PANEL2);
        UIManager.put("ComboBox.foreground", TXT);
        UIManager.put("ScrollPane.background", BG2);
        UIManager.put("Viewport.background", BG2);
        UIManager.put("ToolTip.background", PANEL);
        UIManager.put("ToolTip.foreground", TXT);
        UIManager.put("Button.select", hex("#1c2537"));
    }

    static Color hex(String s) {
        return new Color(Integer.parseInt(s.substring(1), 16));
    }

    static Font mono(int size, int style) { return new Font(MONO, style, size); }

    static Border pad(int t, int l, int b, int r) { return new EmptyBorder(t, l, b, r); }
    static Border cardBorder() {
        return new CompoundBorder(new MatteBorder(1, 1, 1, 1, LINE), pad(10, 12, 10, 12));
    }

    // ---------------------------------------------------------------- build
    static void build() {
        frame = new JFrame("AGENT SOMA · 工作台");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG);
        frame.setSize(1560, 980);
        frame.setMinimumSize(new Dimension(1180, 720));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(new SearchBarLayout().wrap(buildCenter(), buildRight()), BorderLayout.CENTER);
        frame.setContentPane(root);
        frame.setVisible(true);
    }

    static JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout(14, 0));
        h.setBackground(BG2);
        h.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, LINE), pad(14, 20, 14, 20)));

        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        title.setOpaque(false);
        JLabel logo = new JLabel("◤ SOMA");
        logo.setFont(mono(22, Font.BOLD));
        logo.setForeground(ACC);
        title.add(logo);
        JLabel sub = new JLabel("自组织多智能体 · 桌面工作台");
        sub.setFont(mono(12, Font.PLAIN));
        sub.setForeground(DIM);
        title.add(sub);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 4));
        right.setOpaque(false);

        right.add(dotGroup());
        right.add(divider());
        right.add(llmGroup());
        right.add(divider());
        right.add(mgrGroup());
        right.add(divider());
        JButton open = flatBtn("打开经典控制台", ACC, 12);
        open.addActionListener(e -> openBrowser(BASE + "/index.html"));
        right.add(open);
        JButton refresh = flatBtn("刷新", ACC2, 12);
        refresh.addActionListener(e -> refreshAll());
        right.add(refresh);

        h.add(title, BorderLayout.WEST);
        h.add(right, BorderLayout.EAST);
        return h;
    }

    static JComponent divider() {
        JLabel d = new JLabel("│");
        d.setForeground(LINE_S);
        d.setFont(mono(13, Font.PLAIN));
        return d;
    }

    static JPanel dotGroup() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        p.setOpaque(false);
        healthDot = dot(FAINT);
        healthText = new JLabel("服务");
        healthText.setFont(mono(12, Font.PLAIN));
        healthText.setForeground(DIM);
        p.add(healthDot); p.add(healthText);
        backendText = new JLabel("· 未连接");
        backendText.setFont(mono(11, Font.PLAIN));
        backendText.setForeground(FAINT);
        p.add(backendText);
        return p;
    }

    static JPanel llmGroup() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        p.setOpaque(false);
        llmDot = dot(FAINT);
        llmText = new JLabel("模型");
        llmText.setFont(mono(12, Font.PLAIN));
        llmText.setForeground(DIM);
        p.add(llmDot); p.add(llmText);
        return p;
    }

    static JPanel mgrGroup() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        p.setOpaque(false);
        JLabel l = new JLabel("管理者");
        l.setFont(mono(12, Font.PLAIN));
        l.setForeground(DIM);
        mgrText = new JLabel("—");
        mgrText.setFont(mono(12, Font.BOLD));
        mgrText.setForeground(VIOLET);
        p.add(l); p.add(mgrText);
        return p;
    }

    static JPanel buildCenter() {
        JPanel c = new JPanel(new BorderLayout(0, 10));
        c.setBackground(BG);

        // goal input
        JPanel input = new JPanel(new BorderLayout(0, 0));
        input.setBackground(BG2);
        input.setBorder(new CompoundBorder(new MatteBorder(1, 1, 1, 1, LINE_S), pad(12, 14, 12, 14)));

        JPanel inTop = new JPanel(new BorderLayout());
        inTop.setOpaque(false);
        JLabel g = new JLabel("任务目标 TASK OBJECTIVE");
        g.setFont(mono(11, Font.BOLD));
        g.setForeground(ACC);
        runStatus = new JLabel("就绪");
        runStatus.setFont(mono(11, Font.PLAIN));
        runStatus.setForeground(DIM);
        inTop.add(g, BorderLayout.WEST);
        inTop.add(runStatus, BorderLayout.EAST);

        goalArea = new JTextArea(3, 0);
        goalArea.setFont(mono(14, Font.PLAIN));
        goalArea.setForeground(TXT);
        goalArea.setBackground(PANEL);
        goalArea.setCaretColor(ACC);
        goalArea.setLineWrap(true);
        goalArea.setWrapStyleWord(true);
        goalArea.setBorder(new CompoundBorder(new MatteBorder(1, 1, 1, 1, LINE), pad(8, 10, 8, 10)));
        goalArea.setText("进入U9页面查找座椅总成的相关信息，然后汇总成csv");
        goalArea.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                    e.consume(); run();
                }
            }
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        btns.setOpaque(false);
        runBtn = accBtn("▶ 运行", ACC, 13);
        runBtn.addActionListener(e -> run());
        stopBtn = ghostBtn("■ 停止", ROSE, 13);
        stopBtn.setEnabled(false);
        stopBtn.addActionListener(e -> stop());
        JLabel hint = new JLabel("Ctrl+Enter 快捷运行");
        hint.setFont(mono(11, Font.PLAIN));
        hint.setForeground(FAINT);
        btns.add(runBtn); btns.add(stopBtn); btns.add(hint);

        input.add(inTop, BorderLayout.NORTH);
        input.add(goalArea, BorderLayout.CENTER);
        input.add(btns, BorderLayout.SOUTH);

        // trace
        JPanel traceWrap = new JPanel(new BorderLayout(0, 6));
        traceWrap.setBackground(BG);
        JPanel tHead = new JPanel(new BorderLayout());
        tHead.setOpaque(false);
        JLabel t = new JLabel("执行轨迹 EXECUTION TRACE");
        t.setFont(mono(11, Font.BOLD));
        t.setForeground(DIM);
        finalLabel = new JLabel("");
        finalLabel.setFont(mono(11, Font.PLAIN));
        finalLabel.setForeground(OK);
        tHead.add(t, BorderLayout.WEST);
        tHead.add(finalLabel, BorderLayout.EAST);

        tracePanel = new JPanel();
        tracePanel.setLayout(new BoxLayout(tracePanel, BoxLayout.Y_AXIS));
        tracePanel.setBackground(BG);
        tracePanel.setBorder(pad(4, 6, 4, 6));
        traceScroll = new JScrollPane(tracePanel);
        traceScroll.setBorder(new MatteBorder(1, 1, 1, 1, LINE));
        traceScroll.getVerticalScrollBar().setUnitIncrement(16);
        traceScroll.setBackground(BG2);
        traceWrap.add(tHead, BorderLayout.NORTH);
        traceWrap.add(traceScroll, BorderLayout.CENTER);

        c.add(input, BorderLayout.NORTH);
        c.add(traceWrap, BorderLayout.CENTER);
        return c;
    }

    static JPanel buildRight() {
        JPanel r = new JPanel();
        r.setLayout(new BoxLayout(r, BoxLayout.Y_AXIS));
        r.setBackground(BG);

        r.add(section("人工审批 APPROVALS", approvalPanel = listPanel()));
        r.add(boxGap(6));
        r.add(section("人工提问 ASK USER", buildAsk()));
        r.add(boxGap(6));
        r.add(section("历史运行 HISTORY", historyPanel = listPanel()));
        r.add(boxGap(6));
        r.add(buildExport());

        for (Component c : r.getComponents()) {
            if (c instanceof JPanel) ((JPanel) c).setMaximumSize(new Dimension(Integer.MAX_VALUE, ((JPanel) c).getPreferredSize().height));
        }
        return r;
    }

    static JPanel buildAsk() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(BG);
        askLabel = new JLabel("<html>暂无提问</html>");
        askLabel.setFont(mono(12, Font.PLAIN));
        askLabel.setForeground(DIM);
        p.add(askLabel, BorderLayout.NORTH);

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        askField = new JTextField();
        askField.setFont(mono(12, Font.PLAIN));
        askField.setForeground(TXT);
        askField.setBackground(PANEL2);
        askField.setCaretColor(ACC);
        askField.setEnabled(false);
        askSend = accBtn("发送", ACC, 12);
        askSend.setEnabled(false);
        askSend.addActionListener(e -> answerQuestion());
        askField.addActionListener(e -> answerQuestion());
        row.add(askField, BorderLayout.CENTER);
        row.add(askSend, BorderLayout.EAST);
        p.add(row, BorderLayout.CENTER);
        return p;
    }

    static JPanel buildExport() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        JLabel t = new JLabel("导出 EXPORT");
        t.setAlignmentX(0f);
        t.setFont(mono(11, Font.BOLD));
        t.setForeground(DIM);
        p.add(t);
        p.add(Box.createVerticalStrut(8));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        fmtCombo = new JComboBox<>(new String[]{"txt", "md", "json", "csv"});
        fmtCombo.setFont(mono(12, Font.PLAIN));
        fmtCombo.setForeground(TXT);
        fmtCombo.setBackground(PANEL2);
        exportBtn = accBtn("导出结果", ACC, 12);
        exportBtn.addActionListener(e -> export());
        row.add(fmtCombo, BorderLayout.WEST);
        row.add(exportBtn, BorderLayout.CENTER);
        p.add(row);
        return p;
    }

    // ---------------------------------------------------------------- layout helpers
    static final class SearchBarLayout {
        JPanel wrap(JComponent center, JComponent right) {
            JPanel p = new JPanel(new BorderLayout(0, 0));
            p.setBackground(BG);

            // left agents column
            JPanel left = new JPanel(new BorderLayout(0, 6));
            left.setBackground(BG);
            left.setPreferredSize(new Dimension(300, 10));
            JPanel lh = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            lh.setOpaque(false);
            JLabel lt = new JLabel("能力智能体 AGENTS");
            lt.setFont(mono(11, Font.BOLD));
            lt.setForeground(DIM);
            lh.add(lt);
            left.add(lh, BorderLayout.NORTH);
            agentPanel = listPanel();
            left.add(agentPanel, BorderLayout.CENTER);
            agentPanel.setName("agents");

            JPanel mid = new JPanel(new BorderLayout(0, 0));
            mid.setBackground(BG);
            mid.setBorder(pad(0, 14, 14, 12));
            mid.add(center, BorderLayout.CENTER);

            JPanel rr = new JPanel(new BorderLayout(0, 0));
            rr.setBackground(BG);
            rr.setPreferredSize(new Dimension(340, 10));
            rr.setBorder(pad(0, 4, 14, 14));
            rr.add(right, BorderLayout.CENTER);

            p.add(left, BorderLayout.WEST);
            p.add(mid, BorderLayout.CENTER);
            p.add(rr, BorderLayout.EAST);
            return p;
        }
    }

    static JPanel section(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(BG);
        JLabel t = new JLabel(title);
        t.setFont(mono(11, Font.BOLD));
        t.setForeground(DIM);
        p.add(t, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(body);
        sp.setPreferredSize(new Dimension(320, 150));
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        sp.setBorder(new MatteBorder(1, 1, 1, 1, LINE));
        sp.setBackground(BG2);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    static Component boxGap(int h) {
        return Box.createVerticalStrut(h);
    }

    static JPanel listPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG2);
        p.setBorder(pad(4, 4, 4, 4));
        return p;
    }

    // ---------------------------------------------------------------- widgets
    static JLabel dot(Color c) {
        JLabel d = new JLabel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int n = 9, x = (getWidth() - n) / 2, y = (getHeight() - n) / 2;
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
                g2.fillOval(x - 3, y - 3, n + 6, n + 6);
                g2.setColor(c);
                g2.fillOval(x, y, n, n);
                g2.dispose();
            }
        };
        d.setPreferredSize(new Dimension(18, 16));
        return d;
    }

    static JButton flatBtn(String text, Color fg, int size) {
        JButton b = new JButton(text);
        b.setFont(mono(size, Font.PLAIN));
        b.setForeground(fg);
        b.setBackground(PANEL2);
        b.setBorder(new CompoundBorder(new MatteBorder(1, 1, 1, 1, LINE_S), pad(5, 12, 5, 12)));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hover(b, fg);
        return b;
    }

    static JButton accBtn(String text, Color fg, int size) {
        JButton b = flatBtn(text, fg, size);
        return b;
    }

    static JButton ghostBtn(String text, Color fg, int size) {
        JButton b = flatBtn(text, fg, size);
        return b;
    }

    static void hover(JButton b, Color fg) {
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(hex("#182134")); }
            public void mouseExited(MouseEvent e) { b.setBackground(PANEL2); }
        });
    }

    // ---------------------------------------------------------------- backend REST
    static HttpResponse<String> call(String method, String path, String body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(30));
            if ("POST".equals(method)) {
                if (body != null) b.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                else b.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                b.GET();
            }
            return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    static Object getJson(String path) {
        HttpResponse<String> r = call("GET", path, null);
        if (r == null || r.statusCode() >= 300) return null;
        return Json.parse(r.body());
    }

    static Map<String, Object> postJson(String path) {
        HttpResponse<String> r = call("POST", path, null);
        if (r == null) return null;
        return Json.asMap(Json.parse(r.body()));
    }

    // ---------------------------------------------------------------- refresh
    static void refreshAll() {
        new Thread(() -> {
            Object h = getJson("/api/health");
            final boolean up = h instanceof Map;
            Object llm = postJson("/api/llm/connect");
            final boolean llmOk = llm instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) llm).get("connected"));
            Object mgr = getJson("/api/agents/manager");
            final String manager = mgr == null ? "—" : Json.str(Json.field(mgr, "currentManager"));
            String model = llm instanceof Map ? Json.str(Json.field(llm, "model")) : "";

            SwingUtilities.invokeLater(() -> {
                setDot(healthDot, healthText, up ? OK : ROSE, up ? "服务在线" : "服务离线", "服务");
                backendText.setText(up ? "· " + BASE : "· 无法连接 " + BASE);
                setDot(llmDot, llmText, llmOk ? OK : ROSE, llmOk ? "模型在线：" + model : "模型不可用", "模型");
                mgrText.setText(manager);
            });
        }, "refresh-status").start();
        loadAgents();
        loadApprovals();
        loadHistory();
    }

    static void setDot(JLabel dot, JLabel text, Color c, String tip, String base) {
        dot.setForeground(c);
        text.setText(tip);
        text.setToolTipText(tip);
    }

    static void loadAgents() {
        new Thread(() -> {
            Object caps = getJson("/api/agents/capabilities");
            Object scores = getJson("/api/agents/credit-scores");
            Object stats = getJson("/api/agents/stats");
            final List<Map<String, Object>> cList = Json.asListMap(caps);
            final Map<String, Object> s = Json.asMap(scores);
            final Map<String, Object> st = Json.asMap(stats);
            SwingUtilities.invokeLater(() -> {
                agentPanel.removeAll();
                for (Map<String, Object> c : cList) {
                    String label = Json.str(c.get("label"));
                    agentPanel.add(agentCard(label, Json.str(c.get("style")), Json.str(c.get("description")),
                            Json.numInt(s.get(label)), Json.asMap(st.get(label))));
                    agentPanel.add(Box.createVerticalStrut(6));
                }
                if (cList.isEmpty()) {
                    agentPanel.add(emptyNote("无能力数据"));
                }
                agentPanel.revalidate();
                agentPanel.repaint();
            });
        }, "load-agents").start();
    }

    static JPanel agentCard(String label, String style, String desc, int credit, Map<String, Object> st) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(PANEL);
        card.setBorder(cardBorder());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel name = new JLabel(label);
        name.setFont(mono(13, Font.BOLD));
        name.setForeground(TXT);
        JLabel score = new JLabel(String.valueOf(credit));
        score.setFont(mono(12, Font.BOLD));
        score.setForeground(credit >= 60 ? OK : (credit >= 40 ? AMBER : ROSE));
        top.add(name, BorderLayout.WEST);
        top.add(score, BorderLayout.EAST);

        JLabel stl = new JLabel(style == null ? "" : style);
        stl.setFont(mono(11, Font.PLAIN));
        stl.setForeground(ACC2);

        JTextArea d = new JTextArea(desc == null ? "" : desc);
        d.setFont(mono(11, Font.PLAIN));
        d.setForeground(DIM);
        d.setBackground(PANEL);
        d.setLineWrap(true);
        d.setWrapStyleWord(true);
        d.setEditable(false);
        d.setOpaque(false);
        d.setFocusable(false);
        d.setRows(2);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        meta.setOpaque(false);
        String status = Json.str(st.get("status"));
        String task = Json.str(st.get("currentTask"));
        Color sc = "working".equals(status) ? AMBER : (status.isEmpty() || "idle".equals(status) ? OK : DIM);
        JLabel stDot = new JLabel("●");
        stDot.setFont(mono(10, Font.PLAIN));
        stDot.setForeground(sc);
        meta.add(stDot);
        JLabel stt = new JLabel(task.isEmpty() ? (status.isEmpty() ? "空闲" : status) : ("工作中: " + clip(task, 22)));
        stt.setFont(mono(10, Font.PLAIN));
        stt.setForeground(DIM);
        meta.add(stt);

        card.add(top, BorderLayout.NORTH);
        JPanel mid = new JPanel(new BorderLayout(0, 4));
        mid.setOpaque(false);
        mid.add(stl, BorderLayout.NORTH);
        mid.add(d, BorderLayout.CENTER);
        card.add(mid, BorderLayout.CENTER);
        card.add(meta, BorderLayout.SOUTH);
        return card;
    }

    // ---------------------------------------------------------------- approvals
    static void loadApprovals() {
        new Thread(() -> {
            Object a = getJson("/api/approvals");
            final List<Map<String, Object>> list = Json.asListMap(a);
            SwingUtilities.invokeLater(() -> {
                approvalPanel.removeAll();
                if (list.isEmpty()) {
                    approvalPanel.add(emptyNote("无待审批请求"));
                } else {
                    for (Map<String, Object> m : list) {
                        approvalPanel.add(approvalRow(m));
                        approvalPanel.add(Box.createVerticalStrut(6));
                    }
                }
                approvalPanel.revalidate();
                approvalPanel.repaint();
            });
        }, "load-approvals").start();
    }

    static JPanel approvalRow(Map<String, Object> m) {
        final String id = Json.str(m.get("requestId"));
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(PANEL);
        card.setBorder(cardBorder());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        JLabel tool = new JLabel("工具 " + Json.str(m.get("tool")));
        tool.setFont(mono(12, Font.BOLD));
        tool.setForeground(AMBER);
        JTextArea args = new JTextArea(clip(Json.str(m.get("args")), 120));
        args.setFont(mono(11, Font.PLAIN));
        args.setForeground(DIM);
        args.setBackground(PANEL);
        args.setLineWrap(true); args.setWrapStyleWord(true); args.setEditable(false);
        args.setOpaque(false); args.setFocusable(false);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        JButton ok = accBtn("批准", OK, 11);
        ok.addActionListener(e -> decide(id, true));
        JButton no = ghostBtn("拒绝", ROSE, 11);
        no.addActionListener(e -> decide(id, false));
        btns.add(ok); btns.add(no);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(tool, BorderLayout.CENTER);
        top.add(btns, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        card.add(args, BorderLayout.CENTER);
        return card;
    }

    static void decide(String id, boolean approve) {
        new Thread(() -> {
            call("POST", "/api/approvals/" + id + (approve ? "/approve" : "/reject"), null);
            SwingUtilities.invokeLater(SomaDesktop::loadApprovals);
        }, "decide").start();
    }

    // ---------------------------------------------------------------- ask user
    static void answerQuestion() {
        if (pendingQuestionId == null) return;
        String q = pendingQuestionId;
        String ans = askField.getText();
        if (ans.trim().isEmpty()) return;
        new Thread(() -> {
            call("POST", "/api/agent/ask-user/answer?questionId=" + urlEnc(q) + "&answer=" + urlEnc(ans), null);
            SwingUtilities.invokeLater(() -> {
                askField.setText("");
                pendingQuestionId = null;
                askField.setEnabled(false);
                askSend.setEnabled(false);
                askLabel.setForeground(DIM);
                askLabel.setText("<html>已回复：<br>" + esc(ans) + "</html>");
            });
        }, "answer").start();
    }

    // ---------------------------------------------------------------- history
    static void loadHistory() {
        new Thread(() -> {
            Object h = getJson("/api/agent/run/history?limit=30");
            final List<Map<String, Object>> list = Json.asListMap(h);
            SwingUtilities.invokeLater(() -> {
                historyPanel.removeAll();
                if (list.isEmpty()) {
                    historyPanel.add(emptyNote("暂无运行记录"));
                } else {
                    for (Map<String, Object> m : list) {
                        historyPanel.add(historyRow(m));
                        historyPanel.add(Box.createVerticalStrut(4));
                    }
                }
                historyPanel.revalidate();
                historyPanel.repaint();
            });
        }, "load-history").start();
    }

    static JPanel historyRow(Map<String, Object> m) {
        final String runId = Json.str(m.get("runId"));
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setBackground(PANEL);
        card.setBorder(cardBorder());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String term = Json.str(m.get("termination"));
        Color tc = "DONE".equals(term) ? OK : ("FAILED".equals(term) || term.contains("FAIL") ? ROSE : AMBER);
        JLabel goal = new JLabel("<html><span style='color:#e7eefb'>" + esc(clip(Json.str(m.get("goal")), 34)) + "</span></html>");
        goal.setFont(mono(11, Font.BOLD));
        JLabel meta = new JLabel("<html><span style='color:" + colorHex(tc) + "'>" + esc(term.isEmpty() ? "…" : term) + "</span> <span style='color:#5d6a86'>· " + esc(fmtTs(m.get("ts"))) + "</span></html>");
        meta.setFont(mono(10, Font.PLAIN));

        card.add(goal, BorderLayout.NORTH);
        card.add(meta, BorderLayout.CENTER);
        card.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { replay(runId); }
        });
        return card;
    }

    static void replay(String runId) {
        new Thread(() -> {
            Object r = getJson("/api/agent/run/replay/" + runId);
            final List<Map<String, Object>> events = Json.asListMap(r);
            SwingUtilities.invokeLater(() -> {
                resetTrace();
                for (Map<String, Object> ev : events) {
                    String type = Json.str(ev.get("type"));
                    addCard(type, ev);
                }
            });
        }, "replay").start();
    }

    // ---------------------------------------------------------------- export
    static void export() {
        if (currentGoal.isEmpty() && currentFinal.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "暂无运行结果可导出。请先运行一次任务。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String fmt = (String) fmtCombo.getSelectedItem();
        String goal = urlEnc(currentGoal);
        String res = urlEnc(currentFinal);
        String term = urlEnc(currentTermination.isEmpty() ? "DONE" : currentTermination);
        new Thread(() -> {
            Object p = getJson("/api/export/preview?goal=" + goal + "&format=" + fmt + "&result=" + res + "&termination=" + term);
            final Map<String, Object> m = Json.asMap(p);
            SwingUtilities.invokeLater(() -> {
                if (m.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "导出失败，请检查后端是否在线。", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String content = Json.str(m.get("content"));
                String ext = Json.str(m.get("extension"));
                String fname = "agent-report-" + System.currentTimeMillis() + "." + (ext.isEmpty() ? fmt : ext);
                saveFile(fname, content);
            });
        }, "export").start();
    }

    static void saveFile(String fname, String content) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File(fname));
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            java.nio.file.Files.writeString(fc.getSelectedFile().toPath(), content, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(frame, "已导出：" + fc.getSelectedFile().getAbsolutePath(), "导出成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "写入失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------- run / SSE
    static void run() {
        if (running) return;
        String goal = goalArea.getText().trim();
        if (goal.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请输入任务目标。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        currentGoal = goal;
        currentFinal = "";
        currentTermination = "";
        currentRunId = null;
        running = true;
        resetTrace();
        finalLabel.setText("");
        runBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        runStatus.setText("运行中");
        runStatus.setForeground(AMBER);

        new Thread(() -> startSSE(goal), "sse").start();
    }

    static void startSSE(String goal) {
        String path = "/api/agent/run/stream?goal=" + urlEnc(goal)
                + "&conversationId=" + urlEnc("desktop-" + System.currentTimeMillis());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ZERO)
                    .GET()
                    .build();
            HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8));
            String line;
            String event = null;
            StringBuilder data = new StringBuilder();
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        final String ev = event;
                        final String payload = data.toString();
                        SwingUtilities.invokeLater(() -> dispatch(ev, payload));
                    }
                    event = null; data.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                }
            }
        } catch (Exception e) {
            final String msg = e.getMessage();
            SwingUtilities.invokeLater(() -> {
                running = false;
                runBtn.setEnabled(true);
                stopBtn.setEnabled(false);
                runStatus.setText("连接失败");
                runStatus.setForeground(ROSE);
                addSysCard("SSE 连接失败：" + (msg == null ? "未知错误" : msg));
            });
        }
    }

    static void dispatch(String type, String payload) {
        Object parsed = Json.parse(payload);
        Map<String, Object> d = Json.asMap(parsed);
        if ("run:started".equals(type)) {
            currentRunId = Json.str(d.get("runId"));
        }
        addCard(type, d);

        if ("tool:ask-user".equals(type)) {
            pendingQuestionId = Json.str(d.get("questionId"));
            askLabel.setText("<html><span style='color:#a78bfa'>提问：</span><br>" + esc(Json.str(d.get("question"))) + "</html>");
            askLabel.setForeground(TXT);
            askField.setEnabled(true);
            askSend.setEnabled(true);
            askField.requestFocusInWindow();
        }
        if ("tool:approval-request".equals(type)) {
            loadApprovals();
        }
        if ("run:synthesis".equals(type)) {
            currentFinal = Json.str(d.get("finalAnswer"));
        }
        if ("run:completed".equals(type)) {
            currentFinal = Json.str(d.get("finalAnswer"));
            currentTermination = Json.str(d.get("termination"));
            finalLabel.setText("完成 · " + currentTermination);
            finalLabel.setForeground("FAILED".equals(currentTermination) || currentTermination.contains("FAIL") ? ROSE : OK);
            running = false;
            runBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            runStatus.setText("完成");
            runStatus.setForeground(OK);
        }
        if ("run:failed".equals(type)) {
            currentFinal = Json.str(d.get("error"));
            currentTermination = "FAILED";
            finalLabel.setText("失败");
            finalLabel.setForeground(ROSE);
            running = false;
            runBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            runStatus.setText("失败");
            runStatus.setForeground(ROSE);
        }
    }

    static void stop() {
        new Thread(() -> {
            String path = "/api/agent/stop";
            if (currentRunId != null) path += "?runId=" + currentRunId;
            postJson(path);
            SwingUtilities.invokeLater(() -> {
                runStatus.setText("停止中");
                runStatus.setForeground(AMBER);
            });
        }, "stop").start();
    }

    // ---------------------------------------------------------------- trace rendering
    static void resetTrace() {
        tracePanel.removeAll();
        tracePanel.revalidate();
        tracePanel.repaint();
    }

    static void addSysCard(String text) {
        JPanel card = traceCard("system", ROSE, "系统", text);
        appendCard(card);
    }

    static void addCard(String type, Map<String, Object> d) {
        String body = describe(type, d);
        Color tag = tagColor(type);
        appendCard(traceCard(type, tag, type, body));
    }

    static void appendCard(JPanel card) {
        tracePanel.add(card);
        tracePanel.add(Box.createVerticalStrut(6));
        // trim oldest
        while (tracePanel.getComponentCount() / 2 > MAX_CARDS) {
            tracePanel.remove(0);
            tracePanel.remove(0);
        }
        tracePanel.revalidate();
        final JScrollBar sb = traceScroll.getVerticalScrollBar();
        // only autoscroll near bottom
        if (sb.getValue() + sb.getVisibleAmount() >= sb.getMaximum() - 80) {
            SwingUtilities.invokeLater(() -> sb.setValue(sb.getMaximum()));
        }
    }

    static JPanel traceCard(String type, Color tag, String tagText, String body) {
        JPanel card = new JPanel(new BorderLayout(10, 4));
        card.setBackground(BG2);
        card.setBorder(new CompoundBorder(new MatteBorder(1, 1, 1, 1, LINE), pad(8, 8, 8, 8)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));

        JPanel strip = new JPanel(new GridBagLayout());
        strip.setPreferredSize(new Dimension(6, 20));
        strip.setBackground(PANEL);
        JPanel dotl = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tag);
                g2.fillOval(-1, 4, 7, 7);
                g2.dispose();
            }
        };
        dotl.setOpaque(false);
        dotl.setPreferredSize(new Dimension(8, 20));
        strip.add(dotl);

        JTextArea bodyA = new JTextArea(body);
        bodyA.setFont(mono(12, Font.PLAIN));
        bodyA.setForeground(TXT);
        bodyA.setBackground(BG2);
        bodyA.setLineWrap(true);
        bodyA.setWrapStyleWord(true);
        bodyA.setEditable(false);
        bodyA.setOpaque(false);
        bodyA.setFocusable(false);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel tl = new JLabel(tagText);
        tl.setFont(mono(10, Font.BOLD));
        tl.setForeground(tag);
        head.add(tl, BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout(0, 3));
        content.setOpaque(false);
        content.add(head, BorderLayout.NORTH);
        content.add(bodyA, BorderLayout.CENTER);

        card.add(strip, BorderLayout.WEST);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    static Color tagColor(String type) {
        if (type == null) return DIM;
        if (type.contains("failed")) return ROSE;
        if (type.startsWith("tool:")) return AMBER;
        if (type.startsWith("step:")) return ACC2;
        if (type.startsWith("wave:") || type.startsWith("round:") || type.contains("elected")) return VIOLET;
        if (type.startsWith("run:")) return ACC;
        return DIM;
    }

    static String describe(String type, Map<String, Object> d) {
        if (type == null) return dump(d);
        switch (type) {
            case "run:started":
                return "目标：" + Json.str(d.get("goal"));
            case "run:plan": {
                List<Map<String, Object>> steps = Json.asListMap(d.get("steps"));
                StringBuilder sb = new StringBuilder("拆解为 " + steps.size() + " 步：");
                int i = 0;
                for (Map<String, Object> s : steps) {
                    sb.append("\n  · ").append(Json.str(s.get("step"))).append(". ")
                      .append(clip(Json.str(s.get("goal")), 60))
                      .append("  → ").append(Json.str(s.get("worker")));
                    if (++i >= 24) { sb.append("\n  …"); break; }
                }
                return sb.toString();
            }
            case "run:iteration":
                return "第 " + Json.str(d.get("iteration")) + " 轮决策 → " + Json.str(d.get("decision"))
                        + "\n" + Json.str(d.get("reason"));
            case "run:allocation":
                return "Step " + Json.str(d.get("step")) + " 分配给 " + Json.str(d.get("worker"))
                        + "\n" + Json.str(d.get("goal"));
            case "wave:started":
                return "第 " + Json.str(d.get("wave")) + " 波并行开始 → steps " + d.get("stepNums");
            case "wave:completed":
                return "第 " + Json.str(d.get("wave")) + " 波完成 → steps " + d.get("stepNums");
            case "step:status":
                return "Step " + Json.str(d.get("step")) + "  [" + Json.str(d.get("status")) + "]  " + Json.str(d.get("worker"))
                        + (Json.str(d.get("output")).isEmpty() ? "" : "\n" + clip(Json.str(d.get("output")), 300));
            case "step:reflection":
                return "Step " + Json.str(d.get("step")) + " 反思#" + Json.str(d.get("iteration"))
                        + "  满足=" + Json.str(d.get("satisfied"))
                        + "\n" + Json.str(d.get("critique"))
                        + (Json.str(d.get("nextAction")).isEmpty() ? "" : "\n→ " + Json.str(d.get("nextAction")));
            case "tool:call":
                return "调用工具 " + Json.str(d.get("tool"))
                        + (Json.str(d.get("args")).isEmpty() ? "" : "\n" + clip(Json.str(d.get("args")), 200));
            case "tool:decision":
                return "工具 " + Json.str(d.get("tool")) + " 决策 → granted=" + Json.str(d.get("granted"));
            case "tool:result":
                return "工具 " + Json.str(d.get("tool")) + " → " + Json.str(d.get("status"))
                        + (Json.str(d.get("output")).isEmpty() ? "" : "\n" + clip(Json.str(d.get("output")), 300));
            case "tool:approval-request":
                return "高风险工具审批 #" + Json.str(d.get("requestId"))
                        + "\n工具 " + Json.str(d.get("tool"))
                        + "\n参数 " + clip(Json.str(d.get("args")), 200);
            case "tool:ask-user":
                return "向用户提问 #" + Json.str(d.get("questionId")) + "\n" + Json.str(d.get("question"));
            case "run:synthesis":
                return "综合答复 SYNTHESIS：\n" + Json.str(d.get("finalAnswer"));
            case "run:completed":
                return "运行完成 [" + Json.str(d.get("termination")) + "]  共 " + Json.str(d.get("iterations")) + " 轮\n"
                        + Json.str(d.get("finalAnswer"));
            case "run:failed":
                return "运行失败：\n" + Json.str(d.get("error"));
            case "run:elected":
                return "本轮管理者选举 → " + Json.str(d.get("winner"));
            case "run:knowledge":
                return "已注入知识上下文（经验召回）";
            case "round:value":
                return "本轮信息价值评估：\n" + dump(d);
            default:
                return dump(d);
        }
    }

    static String dump(Map<String, Object> d) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Object> e : d.entrySet()) {
            if (++i > 8) { sb.append("  …"); break; }
            sb.append(e.getKey()).append(": ").append(clip(Json.str(e.getValue()), 200)).append("\n");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- utils
    static String clip(String s, int n) {
        if (s == null) return "";
        s = s.replace('\r', ' ').replace('\n', ' ');
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String colorHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    static String urlEnc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    static String fmtTs(Object ts) {
        long t = Json.numLong(ts);
        if (t <= 0) return "";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
    }

    static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
            }
        } catch (Exception ignored) {
            JOptionPane.showMessageDialog(frame, "请手动访问：" + url, "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    static JLabel emptyNote(String s) {
        JLabel l = new JLabel(s);
        l.setFont(mono(11, Font.PLAIN));
        l.setForeground(FAINT);
        l.setBorder(pad(10, 10, 10, 10));
        l.setAlignmentX(0f);
        return l;
    }

    // ---------------------------------------------------------------- JSON mini parser
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object parse(String text) {
            if (text == null || text.isBlank()) return null;
            try {
                Json j = new Json(text.trim());
                Object v = j.value();
                j.ws();
                return v;
            } catch (Exception e) {
                return null;
            }
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object value() {
            ws();
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': i += 4; return Boolean.TRUE;
                case 'f': i += 5; return Boolean.FALSE;
                case 'n': i += 4; return null;
                default: return number();
            }
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (i < s.length()) {
                ws();
                String key = string();
                ws();
                if (i < s.length() && s.charAt(i) == ':') i++;
                Object v = value();
                m.put(key, v);
                ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == '}') { i++; break; }
            }
            return m;
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; // [
            ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (i < s.length()) {
                l.add(value());
                ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == ']') { i++; break; }
            }
            return l;
        }

        private String string() {
            i++; // "
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (i + 4 <= s.length()) {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                i += 4;
                            }
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object number() {
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '-' || s.charAt(i) == '+'
                    || s.charAt(i) == '.' || s.charAt(i) == 'e' || s.charAt(i) == 'E')) i++;
            String n = s.substring(start, i);
            try {
                if (n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0) {
                    return Double.parseDouble(n);
                }
                return Long.parseLong(n);
            } catch (Exception e) {
                return 0L;
            }
        }

        // ---- typed accessors ----
        static String str(Object o) { return o == null ? "" : String.valueOf(o); }
        static long numLong(Object o) {
            if (o instanceof Number) return ((Number) o).longValue();
            if (o instanceof CharSequence) { try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; } }
            return 0L;
        }
        static int numInt(Object o) { return (int) numLong(o); }
        static Object field(Object o, String k) {
            Map<String, Object> m = asMap(o);
            return m.get(k);
        }
        @SuppressWarnings("unchecked")
        static Map<String, Object> asMap(Object o) {
            if (o instanceof Map) return (Map<String, Object>) o;
            return new LinkedHashMap<>();
        }
        @SuppressWarnings("unchecked")
        static List<Map<String, Object>> asListMap(Object o) {
            List<Map<String, Object>> out = new ArrayList<>();
            if (o instanceof List) {
                for (Object e : (List<Object>) o) {
                    if (e instanceof Map) out.add((Map<String, Object>) e);
                }
            }
            return out;
        }
    }

    }