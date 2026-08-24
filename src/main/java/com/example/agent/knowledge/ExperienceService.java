package com.example.agent.knowledge;

import com.example.agent.service.EmbeddingVectorClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 优质经验沉淀服务：自动收集的执行经验先落为「候选」，按语义相似度去重并累计重复次数；
 * 只有「重复出现（≥2 次）且经人工认可」的候选才判定为优质经验（quality），进入 Planner 召回。
 * <p>重复判定：新经验与已有同类经验（EXPERIENCE/PITFALL）的向量余弦相似度（fail-open 回退 bigram）
 * 超过阈值即视为同一经验重复出现，重复计数 +1；否则新建候选节点。
 */
@Service
public class ExperienceService {

    private static final Logger log = LoggerFactory.getLogger(ExperienceService.class);

    /** 语义相似度阈值：新经验与已有经验超过该值视为同一经验重复出现 */
    private static final double SIMILARITY_THRESHOLD = 0.78;
    /** 判定优质经验所需的最小重复次数（重复且被人工认可） */
    private static final int MIN_REPEAT = 2;

    private final KnowledgeGraphService graphService;
    private final EmbeddingVectorClient vectorClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExperienceService(KnowledgeGraphService graphService, EmbeddingVectorClient vectorClient) {
        this.graphService = graphService;
        this.vectorClient = vectorClient;
    }

    /**
     * 收集一条执行经验：与已有同类经验去重合并（重复 +1）或新建候选节点。
     *
     * @return 最终落库的经验节点
     */
    public KnowledgeNodeEntity collectExperience(String runId, String goal, String summary, boolean ok) {
        String type = ok ? "EXPERIENCE" : "PITFALL";
        String name = (ok ? "经验:" : "教训:") + clip(goal);

        List<KnowledgeNodeEntity> existing = graphService.findByType(type);
        KnowledgeNodeEntity matched = findMatched(existing, name, goal);

        if (matched != null) {
            Map<String, String> props = readProps(matched);
            int repeat = parseInt(props.get("repeatCount"), 1) + 1;
            props.put("repeatCount", String.valueOf(repeat));
            appendRunId(props, runId);
            props.put("lastSeenGoal", nz(goal));
            props.put("lastSeenAt", Instant.now().toString());
            props.put("latestSummary", nz(summary));
            matched.setContent(mergeContent(matched.getContent(), summary, repeat));
            saveProps(matched, props);
            log.info("经验重复出现 id={} repeat={} type={}", matched.getId(), repeat, type);
            return matched;
        }

        Map<String, String> props = new LinkedHashMap<>();
        props.put("repeatCount", "1");
        props.put("approved", "PENDING");
        props.put("runIds", nz(runId));
        props.put("success", String.valueOf(ok));
        props.put("lastSeenGoal", nz(goal));
        props.put("lastSeenAt", Instant.now().toString());
        KnowledgeNodeEntity node = graphService.upsertNode(name, type, summary, props);
        log.info("新经验候选落库 id={} type={}", node.getId(), type);
        return node;
    }

    /** 找到与本次经验匹配的已有节点：优先同名（同目标重复执行），其次语义相似度超阈值。 */
    private KnowledgeNodeEntity findMatched(List<KnowledgeNodeEntity> existing, String name, String goal) {
        for (KnowledgeNodeEntity n : existing) {
            if (name.equalsIgnoreCase(n.getName())) {
                return n;
            }
        }
        if (existing.isEmpty() || goal == null || goal.isBlank()) {
            return null;
        }
        List<String> texts = new ArrayList<>(existing.size());
        for (KnowledgeNodeEntity n : existing) {
            texts.add(text(n));
        }
        double[] vec = vectorClient.similarity(goal, texts);
        KnowledgeNodeEntity best = null;
        double bestScore = 0;
        for (int i = 0; i < existing.size(); i++) {
            double score = (vec != null && i < vec.length) ? vec[i] : bigramOverlap(goal, texts.get(i));
            if (score > bestScore) {
                bestScore = score;
                best = existing.get(i);
            }
        }
        if (best != null && bestScore >= SIMILARITY_THRESHOLD) {
            log.info("经验语义匹配 id={} score={}", best.getId(), String.format("%.3f", bestScore));
            return best;
        }
        return null;
    }

    /** 优质经验判定：重复次数达标（≥2）且已被人工认可。 */
    public boolean isQuality(KnowledgeNodeEntity node) {
        if (node == null) {
            return false;
        }
        Map<String, String> props = readProps(node);
        return parseInt(props.get("repeatCount"), 1) >= MIN_REPEAT
                && "APPROVED".equals(props.get("approved"));
    }

    public int repeatCount(KnowledgeNodeEntity node) {
        return parseInt(readProps(node).get("repeatCount"), 1);
    }

    public String approvedState(KnowledgeNodeEntity node) {
        String v = readProps(node).get("approved");
        return v == null || v.isBlank() ? "PENDING" : v;
    }

    /** 人工认可一条经验；若重复次数已达标则自动升级为优质经验。 */
    public KnowledgeNodeEntity approve(String id) {
        return setApproved(id, "APPROVED");
    }

    /** 人工否决一条经验（不再召回）。 */
    public KnowledgeNodeEntity reject(String id) {
        return setApproved(id, "REJECTED");
    }

    private KnowledgeNodeEntity setApproved(String id, String state) {
        KnowledgeNodeEntity node = graphService.findNode(id);
        if (node == null) {
            return null;
        }
        Map<String, String> props = readProps(node);
        props.put("approved", state);
        if ("APPROVED".equals(state)) {
            props.put("approvedAt", Instant.now().toString());
        }
        saveProps(node, props);
        boolean quality = isQuality(node);
        log.info("经验人工{} id={} quality={}", state, id, quality);
        return node;
    }

    /** 全量经验视图（候选 + 优质），供前端经验沉淀面板展示。 */
    public List<Map<String, Object>> listViews() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String type : List.of("EXPERIENCE", "PITFALL")) {
            for (KnowledgeNodeEntity n : graphService.findByType(type)) {
                out.add(view(n));
            }
        }
        out.sort((a, b) -> Integer.compare((int) b.get("repeatCount"), (int) a.get("repeatCount")));
        return out;
    }

    public Map<String, Object> view(KnowledgeNodeEntity n) {
        Map<String, String> props = readProps(n);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("name", n.getName());
        m.put("type", n.getType());
        m.put("content", n.getContent());
        m.put("repeatCount", parseInt(props.get("repeatCount"), 1));
        m.put("approved", approvedState(n));
        m.put("quality", isQuality(n));
        m.put("runIds", props.getOrDefault("runIds", ""));
        m.put("updatedAt", n.getUpdatedAt() == null ? "" : n.getUpdatedAt().toString());
        return m;
    }

    // ---------- 工具 ----------

    private String text(KnowledgeNodeEntity n) {
        return (n.getName() == null ? "" : n.getName())
                + " " + (n.getContent() == null ? "" : n.getContent());
    }

    private String mergeContent(String oldContent, String newSummary, int repeat) {
        String head = oldContent == null || oldContent.isBlank()
                ? "" : oldContent.split("\n【重复")[0];
        return head + "\n【重复出现 " + repeat + " 次】最近一次:\n" + nz(newSummary);
    }

    private void appendRunId(Map<String, String> props, String runId) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        Set<String> ids = new HashSet<>(List.of(props.getOrDefault("runIds", "").split(",")));
        ids.remove("");
        ids.add(runId);
        props.put("runIds", String.join(",", ids));
    }

    private Map<String, String> readProps(KnowledgeNodeEntity node) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            JsonNode n = objectMapper.readTree(node.getPropertiesJson() == null ? "{}" : node.getPropertiesJson());
            if (n != null) {
                n.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText("")));
            }
        } catch (Exception ignored) {
            // 解析失败按空属性处理
        }
        return out;
    }

    private void saveProps(KnowledgeNodeEntity node, Map<String, String> props) {
        Map<String, String> safe = props == null ? new HashMap<>() : props;
        try {
            node.setPropertiesJson(objectMapper.writeValueAsString(safe));
        } catch (Exception e) {
            node.setPropertiesJson("{}");
        }
        node.setUpdatedAt(Instant.now());
        graphService.save(node);
    }

    private int parseInt(String v, int dft) {
        try {
            return v == null || v.isBlank() ? dft : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return dft;
        }
    }

    private double bigramOverlap(String a, String b) {
        Set<String> sa = bigrams(a);
        Set<String> sb = bigrams(b);
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0;
        }
        long inter = sa.stream().filter(sb::contains).count();
        return (double) inter / Math.min(sa.size(), sb.size());
    }

    private Set<String> bigrams(String s) {
        Set<String> set = new HashSet<>();
        if (s == null || s.length() < 2) {
            return set;
        }
        for (int i = 0; i < s.length() - 1; i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    private String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 180 ? s.substring(0, 180) : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
