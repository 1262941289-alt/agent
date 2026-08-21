package com.example.agent.cleaning;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 料号版本拆分：对料号类列，把尾部版本码（如 -A05 / -B01 / -C01）与基础料号分开，
 * 派生 _base / _version 两个字段。基础料号相同、版本不同即为「版本差异」。
 */
@Component
public class VersionSplitRule implements CleaningRule {

    private static final Pattern PART_KEY = Pattern.compile(
            "(?i)料号|型号|编号|物料号|物料|零件|编码|part|model|code|sku");
    private static final Pattern VERSION_TAIL = Pattern.compile("^(.+)-([A-Z]\\d{2,4})$");

    @Override
    public String name() {
        return "料号版本拆分";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<CleaningChange> apply(Map<String, String> row) {
        List<CleaningChange> changes = new ArrayList<>();
        for (String key : new ArrayList<>(row.keySet())) {
            if (!PART_KEY.matcher(key).find()) {
                continue;
            }
            String v = row.get(key);
            if (v == null || v.isEmpty()) {
                continue;
            }
            Matcher m = VERSION_TAIL.matcher(v);
            if (!m.matches()) {
                continue;
            }
            String base = m.group(1);
            String ver = m.group(2);
            if (base.isEmpty()) {
                continue;
            }
            String baseKey = key + "_base";
            String verKey = key + "_version";
            if (!row.containsKey(baseKey)) {
                row.put(baseKey, base);
                changes.add(new CleaningChange(name(), baseKey, null, base, true));
            }
            if (!row.containsKey(verKey)) {
                row.put(verKey, ver);
                changes.add(new CleaningChange(name(), verKey, null, ver, true));
            }
        }
        return changes;
    }
}