package com.example.agent.cleaning;

import com.example.agent.entity.AnnotationRuleEntity;
import com.example.agent.repository.AnnotationRuleRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工标注规则：把人工纠错沉淀的确定性规则（字段 + 原始值 → 纠正值）套用到新数据上。
 * 这是「规则兜底、顶替 LLM」的关键一环，排在所有自动清洗规则之前执行。
 */
@Component
public class AnnotationCorrectionRule implements CleaningRule {

    private final AnnotationRuleRepository ruleRepository;

    private volatile Map<String, String> cache = Map.of();
    private volatile Instant lastRefresh = Instant.EPOCH;

    public AnnotationCorrectionRule(AnnotationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    public String name() {
        return "人工标注规则";
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public List<CleaningChange> apply(Map<String, String> row) {
        Map<String, String> rules = currentRules();
        if (rules.isEmpty()) {
            return List.of();
        }
        List<CleaningChange> changes = new ArrayList<>();
        for (Map.Entry<String, String> e : new ArrayList<>(row.entrySet())) {
            String corrected = rules.get(e.getKey() + "\u0000" + e.getValue());
            if (corrected != null && !corrected.equals(e.getValue())) {
                String before = e.getValue();
                row.put(e.getKey(), corrected);
                changes.add(new CleaningChange(name(), e.getKey(), before, corrected, false));
            }
        }
        return changes;
    }

    /** 规则表做轻量 TTL 缓存，避免逐行查询；阈值很短以保证人工标注后尽快生效。 */
    private Map<String, String> currentRules() {
        if (Duration.between(lastRefresh, Instant.now()).toSeconds() > 3) {
            Map<String, String> m = new HashMap<>();
            for (AnnotationRuleEntity r : ruleRepository.findAll()) {
                m.put(r.getFieldName() + "\u0000" + r.getRawValue(), r.getCorrectedValue());
            }
            cache = Map.copyOf(m);
            lastRefresh = Instant.now();
        }
        return cache;
    }
}