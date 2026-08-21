package com.example.agent.cleaning;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 规格码解析：识别「直径*长度」类尺寸规格（如 ST4.8*9.5），
 * 把规格与材质/标准前缀（如 GB845）拆开，派生 _spec / _material 字段。
 */
@Component
public class SpecCodeParseRule implements CleaningRule {

    private static final Pattern DIM = Pattern.compile("\\d+(?:\\.\\d+)?\\s*[xX*×]\\s*\\d+(?:\\.\\d+)?");

    @Override
    public String name() {
        return "规格码解析";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public List<CleaningChange> apply(Map<String, String> row) {
        List<CleaningChange> changes = new ArrayList<>();
        for (String key : new ArrayList<>(row.keySet())) {
            String v = row.get(key);
            if (v == null || v.isEmpty()) {
                continue;
            }
            String spec = locateSpec(v);
            if (spec == null) {
                continue;
            }
            String normalized = normalizeSpec(spec);
            String specKey = key + "_spec";
            if (!row.containsKey(specKey)) {
                row.put(specKey, normalized);
                changes.add(new CleaningChange(name(), specKey, null, normalized, true));
            }
            String material = extractMaterial(v, spec);
            if (!material.isEmpty()) {
                String matKey = key + "_material";
                if (!row.containsKey(matKey)) {
                    row.put(matKey, material);
                    changes.add(new CleaningChange(name(), matKey, null, material, true));
                }
            }
        }
        return changes;
    }

    /** 在按 '-' 拆分的分段中，定位第一段含尺寸规格的分段。 */
    private String locateSpec(String v) {
        for (String part : v.split("-")) {
            if (!part.isEmpty() && DIM.matcher(part).find()) {
                return part;
            }
        }
        return null;
    }

    private String extractMaterial(String v, String spec) {
        int idx = v.lastIndexOf(spec);
        if (idx <= 0) {
            return "";
        }
        return v.substring(0, idx).replaceAll("[-_\\s]+$", "").strip();
    }

    private String normalizeSpec(String spec) {
        return spec.replace('×', '*').replace('x', '*').replace('X', '*').replace(" ", "");
    }
}