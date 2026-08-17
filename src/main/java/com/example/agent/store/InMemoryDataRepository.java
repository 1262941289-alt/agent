package com.example.agent.store;

import com.example.agent.model.DataItem;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版数据源（演示用）。
 * 接入真实业务时，替换为数据库/接口实现 {@link DataRepository} 即可。
 */
@Repository
public class InMemoryDataRepository implements DataRepository {

    private final ConcurrentMap<String, DataItem> data = new ConcurrentHashMap<>();

    public InMemoryDataRepository() {
        seed();
    }

    private void seed() {
        add("D001", "客户张三，企业客户，注册地上海，2026年累计交易金额 320000 元，涉及跨境资金往来，无重大异常记录。");
        add("D002", "客户李四，个人客户，注册地广州，单笔大额转账 680000 元，收款方为高风险地区账户，近期频繁拆分转账。");
        add("D003", "客户王五，企业客户，注册地深圳，2026年累计交易金额 120000 元，业务往来正常，无异常。");
        add("D004", "客户赵六，个人客户，注册地北京，交易金额 50000 元，资金流向涉及敏感地区，身份信息存在异常。");
        add("D005", "客户孙七，机构客户，注册地杭州，2026年累计交易金额 2600000 元，存在多次与高风险地区往来记录。");
        add("D006", "客户周八，企业客户，注册地成都，交易金额 30000 元，交易记录正常，无风险特征。");
    }

    private void add(String id, String content) {
        data.put(id, new DataItem(id, content));
    }

    @Override
    public List<DataItem> findAll() {
        return new ArrayList<>(data.values());
    }

    @Override
    public Optional<DataItem> findById(String id) {
        return Optional.ofNullable(data.get(id));
    }
}
