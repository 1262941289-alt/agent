package com.example.agent.service;

import com.example.agent.dto.DataItemInput;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件批量导入解析器：将上传文件解析为 {@link DataItemInput} 列表。
 * 支持三种格式：
 * <ul>
 *   <li>JSON 数组：{@code [{"id":"D001","content":"..."}, ...]}</li>
 *   <li>JSON Lines：每行一个 JSON 对象</li>
 *   <li>纯文本：每行一条数据内容（ID 自动生成）</li>
 * </ul>
 */
@Component
public class FileIngestionParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析文件内容为输入对象列表。
     *
     * @param in 文件输入流
     * @return 数据项输入列表
     */
    public List<DataItemInput> parse(InputStream in) {
        String text;
        try {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            throw new IllegalArgumentException("读取文件内容失败: " + e.getMessage(), e);
        }
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