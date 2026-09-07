package com.knowledge.agent.engine;

/**
 * Agent 步骤执行状态（每个 Agent 一次执行 = 一个 step）。
 *
 * @author: lxcechoo@gmail.com
 */
public enum StepStatus {

    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}
