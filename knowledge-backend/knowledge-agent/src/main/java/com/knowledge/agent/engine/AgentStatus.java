package com.knowledge.agent.engine;

/**
 * Agent 任务生命周期状态。
 * <pre>
 *   CREATED ──▶ EXECUTING ──▶ COMPLETED
 *                  │
 *                  ├──────▶ FAILED   （步骤失败/异常/超预算）
 *                  └──────▶ CANCELED （用户取消）
 * </pre>
 * 简化为线性流水线：PLANNING/REFLECTING 等中间态统一归入 EXECUTING（步骤级状态由 {@link StepStatus} 细化）。
 *
 * @author: lxcechoo@gmail.com
 */
public enum AgentStatus {

    CREATED,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELED;

    /** 是否终态（不再变化） */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
