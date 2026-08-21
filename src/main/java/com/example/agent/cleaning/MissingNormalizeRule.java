package com.example.agent.cleaning;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 缺失标准化：把多样的缺失占位（#N/A、N/A、null、-、--、无、(空) 等）统一为空串，
 * 并对所有非空值收拢首尾空白，保证下游规则与 LLM 对「缺失」有一致语义。
 */
@Component
public class MissingNormalizeRule implements CleaningRule {

    private static final Set<String> MISSING = Set.of(
            "#N/A", "N/A", "NA", "NULL", "NONE", "NAN", "NIL",
            "-", "--", "—", "－", "(空)", "（空）", "无");

    @Override
    public String name() {
        return "缺失标准化";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public List<CleaningChange> apply(Map<String, String> row) {
        List<CleaningChange> changes = new ArrayList<>();
        for (Map.Entry<String, String> e : new ArrayList<>(row.entrySet())) {
            String key = e.getKey();
            String v = e.getValue();
            if (v == null) {
                continue;
            }
            String trimmed = v.strip();
            String after = (trimmed.isEmpty() || MISSING.contains(trimmed.toUpperCase(Locale.ROOT)))
                    ? "" : trimmed;
            if (!after.equals(v)) {
                row.put(key, after);
                changes.add(new CleaningChange(name(), key, v, after, false));
            }
        }
        return changes;
    }
}