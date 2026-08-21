package com.example.agent.service;

import com.example.agent.entity.CreditScoreEntity;
import com.example.agent.repository.CreditScoreRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 信用分读写与奖惩：0~100，起点 50，甜点 80，逼近/触及 100 触发过热反噬。
 * <p>阶段二：按子任务执行结果奖惩（成功+奖、失败−罚）；阶段三把 score 项接入选举组合加权。
 */
@Service
public class CreditScoreService {

    public static final int START = 50;
    public static final int SWEET_SPOT = 80;
    public static final int MAX = 100;
    public static final int MIN = 0;
    /** 每次子任务成功的加分 */
    public static final int WIN_REWARD = 5;
    /** 每次子任务失败的扣分 */
    public static final int FAIL_PENALTY = 5;
    /** 触及该分数即视为过热 */
    public static final int OVERHEAT_THRESHOLD = 100;
    /** 过热反噬幅度（十几分的惩罚），把分数拉回甜点附近，防止垄断 */
    public static final int OVERHEAT_BACKLASH = 15;

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
                    return START;
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

    /**
     * 依据子任务执行结果更新信用分：成功向甜点 80 累积加分，失败扣分；
     * 触及过热阈值时触发反噬（显著扣分），防止单一 agent 垄断。
     *
     * @return 更新后的分数
     */
    public int applyOutcome(String label, boolean success) {
        int current = getOrInit(label);
        int next;
        if (success) {
            next = current + WIN_REWARD;
            if (next >= OVERHEAT_THRESHOLD) {
                next = next - OVERHEAT_BACKLASH;
            }
        } else {
            next = current - FAIL_PENALTY;
        }
        next = Math.max(MIN, Math.min(MAX, next));
        update(label, next);
        return next;
    }

    /**
     * 组合评分的 score 项（供阶段三投票使用）：倒 U 形，甜点 80 最优，
     * 分数偏低或逼近 100 都折损——「胜利太多也累加惩罚」的量化形式。
     */
    public double scoreTerm(int score) {
        double d = (double) (score - SWEET_SPOT);
        return Math.exp(-(d * d) / 200.0);
    }

    private void update(String label, int score) {
        CreditScoreEntity entity = repository.findById(label)
                .orElseGet(() -> new CreditScoreEntity(label));
        entity.setScore(score);
        repository.save(entity);
    }
}