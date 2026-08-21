package com.example.agent.service;

import com.example.agent.cleaning.CleaningReport;
import com.example.agent.cleaning.DeterministicCleaningEngine;
import com.example.agent.dto.DataItemInput;
import com.example.agent.entity.DataItemEntity;
import com.example.agent.repository.DataItemRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据接入统一实现：校验并落库到 data_item 表。
 * 后续筛选任务从该表读取待处理（PENDING）数据。
 */
@Service
public class DataIngestionServiceImpl implements DataIngestionService {

    private final DataItemRepository dataItemRepository;
    private final DeterministicCleaningEngine cleaningEngine;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public DataIngestionServiceImpl(DataItemRepository dataItemRepository,
                                    DeterministicCleaningEngine cleaningEngine,
                                    ObjectMapper objectMapper,
                                    AuditLogService auditLogService) {
        this.dataItemRepository = dataItemRepository;
        this.cleaningEngine = cleaningEngine;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public List<String> ingest(List<DataItemInput> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(items.size());
        for (DataItemInput input : items) {
            String content = input.getContent();
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("数据内容 content 不能为空");
            }
            String id = (input.getId() == null || input.getId().isBlank())
                    ? UUID.randomUUID().toString().replace("-", "")
                    : input.getId();
            String sourceType = (input.getSourceType() == null || input.getSourceType().isBlank())
                    ? SOURCE_REST
                    : input.getSourceType();

            DataItemEntity entity = dataItemRepository.findById(id).orElse(new DataItemEntity());
            entity.setId(id);
            entity.setContent(content);
            entity.setSourceType(sourceType);
            entity.setStatus("PENDING");
            applyCleaning(entity, content);
            dataItemRepository.save(entity);
            ids.add(id);
        }
        return ids;
    }

    /**
     * 尝试对扁平 JSON 内容做确定性清洗，产出 cleaned_content 与 cleaning_log。
     * 无法按 JSON 对象解析的内容（纯文本、嵌套等）跳过清洗。
     */
    private void applyCleaning(DataItemEntity entity, String content) {
        try {
            Map<String, String> raw = objectMapper.readValue(
                    content, new TypeReference<LinkedHashMap<String, String>>() {});
            CleaningReport report = cleaningEngine.clean(raw);
            if (report.changes().isEmpty()) {
                entity.setCleanedContent(null);
                entity.setCleaningLog("[]");
            } else {
                entity.setCleanedContent(objectMapper.writeValueAsString(report.cleaned()));
                entity.setCleaningLog(objectMapper.writeValueAsString(report.changes()));
                auditLogService.record("CLEAN", "UPDATE", "data_item", entity.getId(),
                        "确定性清洗 " + report.changes().size() + " 处变更", report.changes(), "system");
            }
        } catch (Exception ignored) {
            entity.setCleanedContent(null);
            entity.setCleaningLog("[]");
        }
    }
}