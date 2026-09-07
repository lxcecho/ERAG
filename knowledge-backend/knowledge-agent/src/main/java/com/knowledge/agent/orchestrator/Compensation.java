package com.knowledge.agent.orchestrator;

import com.knowledge.agent.engine.AgentContext;

/**
 * 补偿动作：当编排图中某节点终态失败时，对已 SUCCESS 的上游节点按逆序执行补偿（Saga 模式）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>best-effort：补偿执行抛异常不阻断回滚链，仅记录 {@code agent_compensation.status=FAILED}；</li>
 *   <li>无副作用 Agent（如检索/分析/报告）无需补偿，由编排层记一条空补偿 SUCCESS 以保证审计完整；</li>
 *   <li>有副作用 Agent（如发邮件/写库）通过实现 {@link Compensable} 提供具体补偿逻辑。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
public interface Compensation {

    /** 补偿描述（审计用，写入 agent_compensation.description） */
    String description();

    /**
     * 执行补偿（best-effort，调用方负责 try/catch 不阻断链）。
     *
     * @param ctx 执行上下文（可读取 artifacts 判断需撤销的内容）
     */
    void execute(AgentContext ctx);
}
