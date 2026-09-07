package com.knowledge.agent.engine;

/**
 * Agent 统一接口：每个专职 Agent（Planner/Knowledge/Analysis/Report）实现此接口。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>Agent 无状态（Spring 单例），所有执行态通过 {@link AgentContext} 传递；</li>
 *   <li>Agent 只读/写 Context 的产物（artifact），不直接操作数据库——持久化由 {@link AgentExecutor} 统一负责；</li>
 *   <li>LLM 调用通过 LangChain4j {@code ChatModel}（由 {@link AgentLlmCaller} 封装），检索通过 {@code KnowledgeSearchTool}（桥接 RAG 层）。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
public interface Agent {

    /** Agent 角色类型 */
    AgentType type();

    /** 角色中文名（用于日志/审计） */
    default String roleName() {
        return type().getRoleName();
    }

    /**
     * 执行 Agent 逻辑。
     *
     * @param ctx 执行上下文（含任务信息、共享产物、配置）
     * @return 执行结果（成功含产物，失败含错误信息）
     */
    AgentResult execute(AgentContext ctx);
}
