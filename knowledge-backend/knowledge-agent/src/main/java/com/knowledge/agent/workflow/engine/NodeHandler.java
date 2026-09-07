package com.knowledge.agent.workflow.engine;

import com.knowledge.agent.workflow.definition.NodeDefinition;
import com.knowledge.agent.workflow.enums.NodeType;

/**
 * 节点处理器策略：每种可执行节点类型（TOOL/LLM）一个实现，由 {@code WorkflowExecutor} 按 type 路由。
 * <p>START/END/HUMAN 不走此策略——START/END 仅作边界，HUMAN 由 Executor 直接处理暂停逻辑。
 *
 * @author: lxcechoo@gmail.com
 */
public interface NodeHandler {

    /** 支持的节点类型 */
    NodeType type();

    /**
     * 执行节点逻辑。
     *
     * @param node 节点定义
     * @param ctx  执行上下文（含上游变量、身份信息）
     * @return 执行结果（成功携带产物，失败携带错误信息）
     */
    NodeExecutionResult handle(NodeDefinition node, WorkflowContext ctx);
}
