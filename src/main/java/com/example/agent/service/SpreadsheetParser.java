package com.example.agent.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表格解析器：按文件头魔数识别真实格式（不信任扩展名），
 * 支持 Excel(.xlsx，多 sheet) 与分隔文本(CSV/TSV)，统一输出为「行 → 列名映射」。
 * 每一行注入 _sheet/_row 元信息，供清洗规则按产品型号与行号追溯。
 */
@Component
public class SpreadsheetParser {

    /** 判断是否为可解析的表格（xlsx 或带表头的分隔文本）。 */
    public boolean supports(byte[] bytes) {
        if (isXlsx(bytes)) {
            return true;
        }
        String text = decodeText(bytes);
        List<String> lines = nonEmptyLines(text);
        if (lines.size() < 2) {
            return false;
        }
        return sniffDelimiter(lines.get(0)) != 0;
    }

    /** 统一入口：自动识别 xlsx / 分隔文本并解析为行列表。 */
    public List<Map<String, String>> parse(byte[] bytes) {
        if (isXlsx(bytes)) {
            return parseXlsx(bytes);
        }
        return parseDelimited(bytes);
    }

    public static String decodeText(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(bytes, Charset.forName("GBK"));
        }
        return utf8;
    }

    private boolean isXlsx(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0x50 && (bytes[1] & 0xFF) == 0x4B;
    }

    private List<Map<String, String>> parseXlsx(byte[] bytes) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                List<String> header = null;
                for (Row row : sheet) {
                    List<String> cells = rowCells(row, fmt, evaluator);
                    if (cells.isEmpty()) {
                        continue;
                    }
                    if (header == null) {
                        header = normalizeHeader(cells);
                        continue;
                    }
                    rows.add(toRow(header, cells, sheetName, row.getRowNum() + 1));
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 解析失败: " + e.getMessage(), e);
        }
        return rows;
    }

    private List<Map<String, String>> parseDelimited(byte[] bytes) {
        List<String> lines = nonEmptyLines(decodeText(bytes));
        if (lines.isEmpty()) {
            return List.of();
        }
        char delim = sniffDelimiter(lines.get(0));
        if (delim == 0) {
            throw new IllegalArgumentException("无法识别表格分隔符");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> header = normalizeHeader(splitLine(lines.get(0), delim));
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = splitLine(lines.get(i), delim);
            boolean allBlank = values.stream().allMatch(String::isBlank);
            if (allBlank) {
                continue;
            }
            rows.add(toRow(header, values, "", i + 1));
        }
        return rows;
    }

    private List<String> rowCells(Row row, DataFormatter fmt, FormulaEvaluator evaluator) {
        List<String> cells = new ArrayList<>();
        for (Cell cell : row) {
            cells.add(fmt.formatCellValue(cell, evaluator));
        }
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isBlank()) {
            cells.remove(cells.size() - 1);
        }
        return cells;
    }

    private Map<String, String> toRow(List<String> header, List<String> values, String sheet, int rowNumber) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String value = i < values.size() ? values.get(i) : "";
            row.put(header.get(i), value);
        }
        row.put("_sheet", sheet);
        row.put("_row", String.valueOf(rowNumber));
        return row;
    }

    /** 空表头补「列N」，重名表头加序号后缀，保证键唯一。 */
    private List<String> normalizeHeader(List<String> raw) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> header = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String h = (raw.get(i) == null || raw.get(i).isBlank()) ? ("列" + (i + 1)) : raw.get(i).strip();
            int count = seen.merge(h, 1, Integer::sum);
            if (count > 1) {
                h = h + "_" + count;
            }
            header.add(h);
        }
        return header;
    }

    private char sniffDelimiter(String line) {
        char[] candidates = {',', '\t', ';'};
        char best = 0;
        int bestCount = 0;
        for (char c : candidates) {
            int count = countOutsideQuotes(line, c);
            if (count > bestCount) {
                bestCount = count;
                best = c;
            }
        }
        return best;
    }

    private int countOutsideQuotes(String line, char target) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && c == target) {
                count++;
            }
        }
        return count;
    }

    private List<String> splitLine(String line, char delim) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == delim) {
                    fields.add(cur.toString().strip());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString().strip());
        return fields;
    }

    private List<String> nonEmptyLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }
}