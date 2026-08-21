package com.example.agent.cleaning;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数值归一：全角数字/小数点/逗号转半角，并去除纯数值内的千分位逗号。
 * 仅作用于「看起来是数值」的字段，避免误改料号等含数字的编码。
 */
@Component
public class NumberNormalizeRule implements CleaningRule {

    @Override
    public String name() {
        return "数值归一";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<CleaningChange> apply(Map<String, String> row) {
        List<CleaningChange> changes = new ArrayList<>();
        for (Map.Entry<String, String> e : new ArrayList<>(row.entrySet())) {
            String key = e.getKey();
            String v = e.getValue();
            if (v == null || v.isEmpty()) {
                continue;
            }
            String after = toHalfWidth(v);
            if (after.matches("[+-]?\\d{1,3}(,\\d{3})+(\\.\\d+)?")) {
                after = after.replace(",", "");
            }
            if (!after.equals(v)) {
                row.put(key, after);
                changes.add(new CleaningChange(name(), key, v, after, false));
            }
        }
        return changes;
    }

    static String toHalfWidth(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - '０' + '0'));
            } else if (c == '．') {
                sb.append('.');
            } else if (c == '，') {
                sb.append(',');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}