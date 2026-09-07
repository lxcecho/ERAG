package com.knowledge.agent.workflow.enums;

/**
 * 节点执行记录状态（workflow_node_run.status）。
 * <p>失败重试：同节点 attempt 递增产生多行 FAILED，最终一次 SUCCESS；HUMAN 节点先 WAITING_HUMAN 后 SUCCESS。
 *
 * @author: lxcechoo@gmail.com
 */
public enum NodeRunStatus {

    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    /** HUMAN 节点已暂停，等待审批 */
    WAITING_HUMAN,
    /** 条件跳过（如 HUMAN 驳回后跳过的下游节点） */
    SKIPPED,
    CANCELED
}
