package com.example.agent.cleaning;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单位提取：把「数值 + 单位」合并的单元格拆成纯数值与单位两个字段（如 9.5mm → 9.5 / mm）。
 */
@Component
public class UnitExtractRule implements CleaningRule {

    private static final Pattern NUM_UNIT = Pattern.compile(
            "^([+-]?\\d+(?:\\.\\d+)?)\\s*(mm|cm|m|km|kg|g|mg|t|pcs|pc|个|支|件|套|箱|米|千米|克|千克|公斤|吨|毫米|厘米|毫升|升)?$");

    @Override
    public String name() {
        return "单位提取";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public List<CleaningChange> apply(Map<String, String> row) {
        List<CleaningChange> changes = new ArrayList<>();
        for (String key : new ArrayList<>(row.keySet())) {
            String v = row.get(key);
            if (v == null || v.isEmpty()) {
                continue;
            }
            Matcher m = NUM_UNIT.matcher(v);
            if (!m.matches() || m.group(2) == null || m.group(2).isEmpty()) {
                continue;
            }
            String value = m.group(1);
            String unit = m.group(2);
            String valueKey = key + "_value";
            String unitKey = key + "_unit";
            if (!row.containsKey(valueKey)) {
                row.put(valueKey, value);
                changes.add(new CleaningChange(name(), valueKey, null, value, true));
            }
            if (!row.containsKey(unitKey)) {
                row.put(unitKey, unit);
                changes.add(new CleaningChange(name(), unitKey, null, unit, true));
            }
        }
        return changes;
    }
}