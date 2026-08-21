package com.example.agent.repository;

import com.example.agent.entity.ItemAnnotationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 数据条目级标注仓库。
 */
public interface ItemAnnotationRepository extends JpaRepository<ItemAnnotationEntity, String> {

    List<ItemAnnotationEntity> findByItemId(String itemId);
}