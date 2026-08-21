package com.example.agent.service;

import com.example.agent.entity.CreditScoreEntity;
import com.example.agent.repository.CreditScoreRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 信用分读写：阶段一仅维护默认 50 分；阶段二/三接入奖惩与过热反噬。
 */
@Service
public class CreditScoreService {

    private final CreditScoreRepository repository;

    public CreditScoreService(CreditScoreRepository repository) {
        this.repository = repository;
    }

    /** 不存在则初始化默认 50 分并返回。 */
    public int getOrInit(String label) {
        return repository.findById(label)
                .map(CreditScoreEntity::getScore)
                .orElseGet(() -> {
                    repository.save(new CreditScoreEntity(label));
                    return 50;
                });
    }

    /** 批量幂等初始化（供启动或后续阶段使用）。 */
    public void ensureSeeded(Collection<String> labels) {
        for (String label : labels) {
            if (!repository.existsById(label)) {
                repository.save(new CreditScoreEntity(label));
            }
        }
    }

    public List<CreditScoreEntity> all() {
        return repository.findAll();
    }
}