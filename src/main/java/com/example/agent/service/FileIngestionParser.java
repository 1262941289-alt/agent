package com.example.agent.service;

import com.example.agent.dto.DataItemInput;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件批量导入解析器：将上传文件解析为 {@link DataItemInput} 列表。
 * <p>依赖 {@link SpreadsheetParser} 做真实格式识别与表格解析，先支持：
 * <ul>
 *   <li>Excel(.xlsx，多 sheet)</li>
 *   <li>分隔文本(CSV/TSV)</li>
 *   <li>JSON 数组 / JSON Lines / 纯文本</li>
 * </ul>
 * 表格的每一行序列化为一条结构化 JSON 写入 content。
 */
@Component
public class FileIngestionParser {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpreadsheetParser spreadsheetParser;

    public FileIngestionParser(SpreadsheetParser spreadsheetParser) {
        this.spreadsheetParser = spreadsheetParser;
    }

    public List<DataItemInput> parse(InputStream in) {
        byte[] bytes;
        try {
            bytes = in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("读取文件内容失败: " + e.getMessage(), e);
        }
        if (bytes.length == 0) {
            return List.of();
        }

        if (spreadsheetParser.supports(bytes)) {
            List<Map<String, String>> rows = spreadsheetParser.parse(bytes);
            List<DataItemInput> items = new ArrayList<>(rows.size());
            for (Map<String, String> row : rows) {
                try {
                    String content = objectMapper.writeValueAsString(row);
                    items.add(new DataItemInput(null, content, DataIngestionService.SOURCE_FILE));
                } catch (Exception e) {
                    throw new IllegalArgumentException("结构化数据序列化失败: " + e.getMessage(), e);
                }
            }
            return items;
        }

        String text = SpreadsheetParser.decodeText(bytes).strip();
        if (text.isEmpty()) {
            return List.of();
        }

        String head = text.stripLeading();
        // JSON 数组
        if (head.startsWith("[")) {
            try {
                return objectMapper.readValue(text, new TypeReference<List<DataItemInput>>() {
                });
            } catch (Exception e) {
                throw new IllegalArgumentException("JSON 数组解析失败: " + e.getMessage(), e);
            }
        }

        // JSON 对象（单条）
        if (head.startsWith("{")) {
            try {
                return List.of(objectMapper.readValue(text, DataItemInput.class));
            } catch (Exception ignored) {
                // 继续按行解析
            }
        }

        // 逐行：JSON Lines 或纯文本
        List<DataItemInput> items = new ArrayList<>();
        for (String line : text.split("\\R")) {
            line = line.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("{")) {
                try {
                    items.add(objectMapper.readValue(line, DataItemInput.class));
                    continue;
                } catch (Exception ignored) {
                    // 回退为纯文本
                }
            }
            items.add(new DataItemInput(null, line, DataIngestionService.SOURCE_FILE));
        }
        return items;
    }
}