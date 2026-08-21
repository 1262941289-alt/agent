package com.example.agent.store;

import com.example.agent.entity.DataItemEntity;
import com.example.agent.model.DataItem;
import com.example.agent.repository.DataItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link DataRepository} 的 JPA 持久化实现，数据项落库到 MySQL。
 */
@Repository
public class JpaDataRepository implements DataRepository {

    private final DataItemRepository dataItemRepository;

    public JpaDataRepository(DataItemRepository dataItemRepository) {
        this.dataItemRepository = dataItemRepository;
    }

    @Override
    public List<DataItem> findAll() {
        return dataItemRepository.findAll().stream()
                .map(this::toModel)
                .toList();
    }

    @Override
    public Optional<DataItem> findById(String id) {
        return dataItemRepository.findById(id).map(this::toModel);
    }

    private DataItem toModel(DataItemEntity entity) {
        return new DataItem(entity.getId(), entity.getContent());
    }
}