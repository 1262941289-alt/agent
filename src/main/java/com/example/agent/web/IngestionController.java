package com.example.agent.web;

import com.example.agent.dto.DataItemInput;
import com.example.agent.service.DataIngestionService;
import com.example.agent.service.FileIngestionParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 数据接入 REST 接口：实时提交数据（单条/批量）与文件批量导入。
 */
@RestController
@RequestMapping("/api/data")
public class IngestionController {

    private final DataIngestionService ingestionService;
    private final FileIngestionParser fileIngestionParser;

    public IngestionController(DataIngestionService ingestionService,
                               FileIngestionParser fileIngestionParser) {
        this.ingestionService = ingestionService;
        this.fileIngestionParser = fileIngestionParser;
    }

    /**
     * 批量接入数据项。
     * POST /api/data
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody List<DataItemInput> items) {
        List<String> ids = ingestionService.ingest(items);
        return ResponseEntity.ok(Map.of("accepted", ids.size(), "ids", ids));
    }

    /**
     * 文件批量导入（JSON 数组 / JSON Lines / 纯文本）。
     * POST /api/data/file
     */
    @PostMapping("/file")
    public ResponseEntity<Map<String, Object>> ingestFile(@RequestParam("file") MultipartFile file) {
        List<DataItemInput> items;
        try {
            items = fileIngestionParser.parse(file.getInputStream());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        List<String> ids = ingestionService.ingest(items);
        return ResponseEntity.ok(Map.of("accepted", ids.size(), "ids", ids));
    }
}