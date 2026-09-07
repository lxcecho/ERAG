package com.knowledge.agent.orchestrator;

import com.knowledge.agent.engine.AgentContext;
import com.knowledge.agent.engine.Agent;

/**
 * 可补偿标记接口：有副作用的 {@link Agent} 实现此接口以提供补偿动作。
 * <p>
 * 编排层通过 {@code agent instanceof Compensable} 判定，<b>不修改 {@code engine.Agent} 接口</b>，
 * 保持 engine 层零侵入与依赖方向（orchestrator → engine）。
 * <p>
 * 示例：发邮件 Agent 实现 Compensable，在 compensation() 中返回"记录召回邮件/通知管理员"的补偿。
 *
 * @author: lxcechoo@gmail.com
 */
public interface Compensable {

    /**
     * 构建本 Agent 本次执行的补偿动作。
     *
     * @param ctx 执行上下文（含本次产物，供补偿判断）
     * @return 补偿动作；返回 null 表示无需补偿（编排层按空补偿记录审计）
     */
    Compensation compensation(AgentContext ctx);
}
