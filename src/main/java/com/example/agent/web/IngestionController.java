package com.example.agent.web;

import com.example.agent.dto.DataItemInput;
import com.example.agent.entity.DataItemEntity;
import com.example.agent.repository.DataItemRepository;
import com.example.agent.service.AuditLogService;
import com.example.agent.service.DataIngestionService;
import com.example.agent.service.FileIngestionParser;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据接入 REST 接口：实时提交数据（单条/批量）与文件批量导入，以及数据列表查询。
 */
@RestController
@RequestMapping("/api/data")
public class IngestionController {

    private final DataIngestionService ingestionService;
    private final FileIngestionParser fileIngestionParser;
    private final DataItemRepository dataItemRepository;
    private final AuditLogService auditLogService;

    public IngestionController(DataIngestionService ingestionService,
                               FileIngestionParser fileIngestionParser,
                               DataItemRepository dataItemRepository,
                               AuditLogService auditLogService) {
        this.ingestionService = ingestionService;
        this.fileIngestionParser = fileIngestionParser;
        this.dataItemRepository = dataItemRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * 批量接入数据项。
     * POST /api/data
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody List<DataItemInput> items) {
        List<String> ids = ingestionService.ingest(items);
        auditLogService.record("INGEST", "CREATE", "data_item", "",
                "批量接入数据 " + ids.size() + " 条", Map.of("accepted", ids.size()), "human");
        return ResponseEntity.ok(Map.of("accepted", ids.size(), "ids", ids));
    }

    /**
     * 文件批量导入（JSON 数组 / JSON Lines / 纯文本 / Excel / CSV）。
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
        auditLogService.record("INGEST", "CREATE", "data_item", "",
                "文件导入数据 " + ids.size() + " 条", Map.of("accepted", ids.size(),
                        "filename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename()), "human");
        return ResponseEntity.ok(Map.of("accepted", ids.size(), "ids", ids));
    }

    /**
     * 分页列出最近接入的数据（含原始/清洗后/清洗日志），供前端标注工作台展示。
     * GET /api/data?limit=50
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@RequestParam(defaultValue = "50") int limit) {
        int n = Math.min(Math.max(limit, 1), 200);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DataItemEntity e : dataItemRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, n))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("sourceType", e.getSourceType());
            m.put("status", e.getStatus());
            m.put("createdAt", e.getCreatedAt());
            m.put("content", e.getContent());
            m.put("cleanedContent", e.getCleanedContent());
            m.put("cleaningLog", e.getCleaningLog());
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }
}