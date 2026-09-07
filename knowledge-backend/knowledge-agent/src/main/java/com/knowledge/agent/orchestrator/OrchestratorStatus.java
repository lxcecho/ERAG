package com.knowledge.agent.orchestrator;

/**
 * 编排引擎任务级状态机（写入 agent_task.status，VARCHAR 列与 engine.AgentStatus 共存不冲突）。
 * <pre>
 *   CREATED ──▶ RUNNING ──┬──▶ COMPLETED（全部节点 SUCCESS）
 *                         ├──▶ ROLLING_BACK ──▶ FAILED（补偿链执行完毕，best-effort）
 *                         ├──▶ FAILED（节点失败且回滚关闭/无补偿）
 *                         └──▶ CANCELED（用户取消）
 * </pre>
 * ROLLING_BACK 为补偿进行中的中间审计态，最终落 FAILED；一任务仅由编排层执行，状态字符串与 engine 层 EXECUTING 不混用。
 *
 * @author: lxcechoo@gmail.com
 */
public enum OrchestratorStatus {

    CREATED,
    RUNNING,
    /** 补偿链回滚进行中（节点终态失败后触发） */
    ROLLING_BACK,
    COMPLETED,
    FAILED,
    CANCELED;

    /** 是否终态（不再变化） */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
