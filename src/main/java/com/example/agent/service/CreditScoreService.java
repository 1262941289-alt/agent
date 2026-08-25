package com.example.agent.service;

import com.example.agent.entity.CreditScoreEntity;
import com.example.agent.repository.CreditScoreRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 信用分系统 v2：任务难度加权 + 努力分 + 干多不罚。
 * <p>核心原则：
 * <ul>
 *   <li>难度加权：不同能力域的任务难度不同，browser 任务天然难于 general</li>
 *   <li>努力分：失败也有部分信用（至少尝试了），不全部 -5</li>
 *   <li>干多不罚：分配次数越多，基础分越高（负载贡献奖励）</li>
 *   <li>无过热反噬：不再惩罚高分 Agent，倒 U 形 scoreTerm 已废弃</li>
 * </ul>
 */
@Service
public class CreditScoreService {

    public static final int START = 50;
    public static final int MAX = 100;
    public static final int MIN = 0;

    private static final Map<String, Double> DIFFICULTY_WEIGHTS = new ConcurrentHashMap<>();

    static {
        DIFFICULTY_WEIGHTS.put("browser", 1.5);
        DIFFICULTY_WEIGHTS.put("data", 1.2);
        DIFFICULTY_WEIGHTS.put("history", 1.0);
        DIFFICULTY_WEIGHTS.put("general", 0.8);
    }

    private final CreditScoreRepository repository;
    private final Map<String, Integer> workloadCache = new ConcurrentHashMap<>();

    public CreditScoreService(CreditScoreRepository repository) {
        this.repository = repository;
    }

    public int getOrInit(String label) {
        return repository.findById(label)
                .map(CreditScoreEntity::getScore)
                .orElseGet(() -> {
                    repository.save(new CreditScoreEntity(label));
                    return START;
                });
    }

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

    public double getDifficultyWeight(String label) {
        return DIFFICULTY_WEIGHTS.getOrDefault(label.toLowerCase(), 1.0);
    }

    /**
     * v2 信用分更新：基于任务难度和执行结果计算增量。
     * <p>成功：基础 +5 × 难度权重（browser 成功得 7.5 分，general 成功得 4 分）
     * <p>失败：基础 -5 ÷ 难度权重（browser 失败扣 3.3 分，general 失败扣 6.25 分）
     * <p>→ 难任务成功奖励更多，难任务失败惩罚更少
     *
     * @param label   能力域标签
     * @param success 是否成功
     * @return 更新后的分数
     */
    public int applyOutcome(String label, boolean success) {
        return applyOutcome(label, success, 0);
    }

    /**
     * 带反思次数的信用分更新：反思次数越多说明越努力，给予部分努力分。
     */
    public int applyOutcome(String label, boolean success, int reflectionCount) {
        int current = getOrInit(label);
        double weight = getDifficultyWeight(label);
        int workload = workloadCache.merge(label, 1, Integer::sum);

        double delta;
        if (success) {
            delta = 5.0 * weight;
            if (reflectionCount > 0) {
                delta += reflectionCount * 0.5;
            }
        } else {
            delta = -5.0 / weight;
            if (reflectionCount > 0) {
                delta += reflectionCount * 0.8;
            }
        }

        double workloadBonus = Math.min(workload * 0.3, 10.0);
        int next = (int) Math.round(current + delta + workloadBonus);
        next = Math.max(MIN, Math.min(MAX, next));
        update(label, next);
        return next;
    }

    /**
     * v2 scoreTerm：线性映射，不再用倒 U 形惩罚低分。
     * <p>分数直接线性归一化为 0~1，高分=高权重，低分=低权重但不归零。
     */
    public double scoreTerm(int score) {
        return (double) score / MAX;
    }

    /**
     * 重置所有信用分到起始值（供全量重置使用）。
     */
    public void resetAll(Collection<String> labels) {
        for (String label : labels) {
            update(label, START);
            workloadCache.remove(label);
        }
    }

    private void update(String label, int score) {
        CreditScoreEntity entity = repository.findById(label)
                .orElseGet(() -> new CreditScoreEntity(label));
        entity.setScore(score);
        repository.save(entity);
    }
}
