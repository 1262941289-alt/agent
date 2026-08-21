package com.example.agent.store;

import org.springframework.stereotype.Component;

/**
 * 当前筛选任务上下文：通过 ThreadLocal 携带 taskId，
 * 使工具写入的分层/属性/决策结果能关联到所属任务。
 */
@Component
public class TaskContext {

    private final ThreadLocal<String> currentTaskId = new ThreadLocal<>();

    public void set(String taskId) {
        currentTaskId.set(taskId);
    }

    public String get() {
        return currentTaskId.get();
    }

    public void clear() {
        currentTaskId.remove();
    }
}