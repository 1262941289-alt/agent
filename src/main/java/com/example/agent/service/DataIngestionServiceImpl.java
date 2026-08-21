package com.example.agent.service;

import com.example.agent.dto.DataItemInput;
import com.example.agent.entity.DataItemEntity;
import com.example.agent.repository.DataItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 数据接入统一实现：校验并落库到 data_item 表。
 * 后续筛选任务从该表读取待处理（PENDING）数据。
 */
@Service
public class DataIngestionServiceImpl implements DataIngestionService {

    private final DataItemRepository dataItemRepository;

    public DataIngestionServiceImpl(DataItemRepository dataItemRepository) {
        this.dataItemRepository = dataItemRepository;
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
            dataItemRepository.save(entity);
            ids.add(id);
        }
        return ids;
    }
}