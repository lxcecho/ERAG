package com.knowledge.agent.workflow.enums;

/**
 * Workflow 任务状态机。
 * <pre>
 *   CREATED → RUNNING ─┬─→ COMPLETED
 *                      ├─→ WAITING_HUMAN →（审批通过）RUNNING ...
 *                      ├─→ FAILED（节点失败且重试耗尽）
 *                      └─→ CANCELED
 * </pre>
 * WAITING_HUMAN 是"人工介入"暂停态：执行到 HUMAN 节点时置此态并返回，
 * 待审批后由 {@code WorkflowExecutor.resume} 恢复为 RUNNING 继续后续节点。
 *
 * @author: lxcechoo@gmail.com
 */
public enum WorkflowStatus {

    CREATED,
    RUNNING,
    /** 等待人工审批（HUMAN 节点暂停） */
    WAITING_HUMAN,
    COMPLETED,
    FAILED,
    CANCELED;

    /** 是否终态（不可再执行） */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }

    /** 是否可恢复执行（从断点继续） */
    public boolean isResumable() {
        return this == CREATED || this == WAITING_HUMAN;
    }
}
