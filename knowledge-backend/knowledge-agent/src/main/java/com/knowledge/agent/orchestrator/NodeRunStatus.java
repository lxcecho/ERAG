package com.knowledge.agent.orchestrator;

/**
 * 编排引擎节点级状态机（写入 agent_node_run.status）。
 * <pre>
 *   PENDING ──▶ RUNNING ──┬──▶ SUCCESS
 *                          ├──▶ RETRYING ──▶ RUNNING ...（attempt &lt;= maxRetries）
 *                          ├──▶ TIMEOUT ──▶ RETRYING/FAILED
 *                          └──▶ FAILED（重试耗尽，触发上游补偿）
 *   回滚跳过的未执行节点：SKIPPED
 * </pre>
 * 重试：同节点 attempt 递增产生多行 RUNNING/FAILED，最终一次 SUCCESS；超时计入失败重试预算。
 *
 * @author: lxcechoo@gmail.com
 */
public enum NodeRunStatus {

    PENDING,
    RUNNING,
    SUCCESS,
    /** 失败/超时后等待重试（backoff 期间） */
    RETRYING,
    FAILED,
    /** Future.get 超时（硬超时触发，计入重试预算） */
    TIMEOUT,
    /** 回滚时跳过的未执行下游节点 */
    SKIPPED,
    CANCELED;

    /** 是否终态（本行不再变化） */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == CANCELED;
    }
}
