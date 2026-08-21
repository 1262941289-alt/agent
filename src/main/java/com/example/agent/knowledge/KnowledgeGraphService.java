package com.example.agent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识图谱服务（GraphRAG 存储层）：
 * <ul>
 *   <li>节点 upsert（累积知识，按 name 幂等去重）</li>
 *   <li>关系写入，支持双向链接（正向边 + 自动反向边，backlink 可双向检索）</li>
 *   <li>关键字检索 + 一跳邻居展开，组装 GraphRAG 上下文供 LLM 增强生成</li>
 * </ul>
 * 当前以 MySQL/JPA 落地；后续可平滑迁移到 Neo4j 或向量检索。
 */
@Service
public class KnowledgeGraphService {

    private final KnowledgeNodeRepository nodeRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeGraphService(KnowledgeNodeRepository nodeRepository,
                                 KnowledgeRelationRepository relationRepository) {
        this.nodeRepository = nodeRepository;
        this.relationRepository = relationRepository;
    }

    // ---------- 节点 ----------

    /** 按 name 幂等 upsert 节点，返回最新节点 */
    @Transactional
    public KnowledgeNodeEntity upsertNode(String name, String type, String content, Map<String, String> properties) {
        KnowledgeNodeEntity node = nodeRepository.findFirstByNameIgnoreCase(name).orElse(null);
        if (node == null) {
            node = new KnowledgeNodeEntity();
            node.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        node.setName(name);
        node.setType(type == null || type.isBlank() ? "CONCEPT" : type);
        node.setContent(content);
        node.setPropertiesJson(writeJson(properties));
        node.setUpdatedAt(Instant.now());
        return nodeRepository.save(node);
    }

    public KnowledgeNodeEntity getOrCreateNode(String name, String type) {
        KnowledgeNodeEntity existing = nodeRepository.findFirstByNameIgnoreCase(name).orElse(null);
        if (existing != null) {
            return existing;
        }
        KnowledgeNodeEntity node = new KnowledgeNodeEntity();
        node.setId(UUID.randomUUID().toString().replace("-", ""));
        node.setName(name);
        node.setType(type == null || type.isBlank() ? "CONCEPT" : type);
        return nodeRepository.save(node);
    }

    public KnowledgeNodeEntity findNode(String id) {
        return nodeRepository.findById(id).orElse(null);
    }

    // ---------- 关系（双向链接） ----------

    @Transactional
    public void addRelation(String sourceName, String relationType, String targetName, boolean bidirectional) {
        KnowledgeNodeEntity source = getOrCreateNode(sourceName, "CONCEPT");
        KnowledgeNodeEntity target = getOrCreateNode(targetName, "CONCEPT");
        addRelationById(source.getId(), relationType, target.getId(), bidirectional);
    }

    @Transactional
    public void addRelationById(String sourceId, String relationType, String targetId, boolean bidirectional) {
        String type = relationType == null || relationType.isBlank() ? "RELATED_TO" : relationType;
        if (!relationRepository.existsBySourceIdAndTargetIdAndRelationType(sourceId, targetId, type)) {
            relationRepository.save(newRelation(sourceId, type, targetId, bidirectional));
        }
        if (bidirectional
                && !relationRepository.existsBySourceIdAndTargetIdAndRelationType(targetId, sourceId, type)) {
            relationRepository.save(newRelation(targetId, type, sourceId, true));
        }
    }

    private KnowledgeRelationEntity newRelation(String sourceId, String type, String targetId, boolean bidirectional) {
        KnowledgeRelationEntity r = new KnowledgeRelationEntity();
        r.setSourceId(sourceId);
        r.setTargetId(targetId);
        r.setRelationType(type);
        r.setBidirectional(bidirectional);
        return r;
    }

    /** 出边（正向链接） */
    public List<KnowledgeRelationEntity> outgoing(String nodeId) {
        return relationRepository.findBySourceId(nodeId);
    }

    /** 入边（反向链接 / backlink） */
    public List<KnowledgeRelationEntity> incoming(String nodeId) {
        return relationRepository.findByTargetId(nodeId);
    }

    // ---------- 检索（GraphRAG） ----------

    /** 关键字检索节点（MVP 用名称包含匹配；语义检索后续接 embedding） */
    public List<KnowledgeNodeEntity> search(String keyword, int k) {
        return nodeRepository.findByNameContainingIgnoreCase(keyword).stream().limit(k).toList();
    }

    /** 按类型检索节点（如 EXPERIENCE / PITFALL / ANNOTATION），供经验召回使用。 */
    public List<KnowledgeNodeEntity> findByType(String type) {
        return nodeRepository.findByType(type);
    }

    /** 组装 GraphRAG 上下文：命中节点 + 一跳邻居（正向/反向链接） */
    public String buildContext(String query, int k) {
        List<KnowledgeNodeEntity> hits = search(query, k);
        StringBuilder sb = new StringBuilder();
        for (KnowledgeNodeEntity n : hits) {
            sb.append("[").append(n.getType()).append("] ").append(n.getName())
                    .append(": ").append(n.getContent() == null ? "" : n.getContent()).append("\n");
            for (KnowledgeRelationEntity rel : outgoing(n.getId())) {
                KnowledgeNodeEntity t = findNode(rel.getTargetId());
                if (t != null) {
                    sb.append("  -> ").append(rel.getRelationType()).append(" -> ").append(t.getName()).append("\n");
                }
            }
            for (KnowledgeRelationEntity rel : incoming(n.getId())) {
                KnowledgeNodeEntity s = findNode(rel.getSourceId());
                if (s != null) {
                    sb.append("  <- ").append(rel.getRelationType()).append(" <- ").append(s.getName()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    // ---------- 人工标注 ----------

    /**
     * 写入一条人工标注（ANNOTATION 节点）：正向=该操作正确且富有成效、值得复用；负向=低效/有误、应避免。
     * 标注作为人类监督信号被 {@code ExperienceRetriever} 召回并注入 Planner，指导后续规划。
     */
    @Transactional
    public void annotate(String goal, boolean positive, String comment) {
        String g = goal == null ? "" : goal;
        String content = "目标: " + g + "\n"
                + "标注: " + (positive ? "正向（该操作正确且富有成效，值得复用）" : "负向（该操作低效或有误，应避免）") + "\n"
                + "说明: " + (comment == null ? "" : comment);
        String nodeName = "标注:" + clipName(g) + ":" + shortId();
        upsertNode(nodeName, "ANNOTATION", content,
                Map.of("positive", String.valueOf(positive), "goal", g));
        if (!g.isBlank()) {
            addRelation(g, "HUMAN_ANNOTATED", nodeName, true);
        }
    }

    private String clipName(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String writeJson(Map<String, String> properties) {
        try {
            return objectMapper.writeValueAsString(properties == null ? Map.of() : properties);
        } catch (Exception e) {
            return "{}";
        }
    }
}