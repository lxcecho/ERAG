package com.knowledge.agent.workflow.engine;

import com.knowledge.agent.tool.ToolContext;
import com.knowledge.agent.tool.ToolExecutor;
import com.knowledge.agent.tool.ToolResult;
import com.knowledge.agent.workflow.definition.NodeDefinition;
import com.knowledge.agent.workflow.enums.NodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TOOL 节点处理器：调用注册中心的工具（复用 {@link ToolExecutor} 完整生命周期）。
 * <p>
 * 复用价值：工具查找/参数校验/权限校验/审计/租户上下文 全部由 ToolExecutor 统一处理，
 * Workflow 层无需重复实现权限与审计链路——Agent 检索与 Workflow 检索权限模型完全一致。
 * <p>入参中的 ${var} 由 {@link VariableResolver} 解析为实际值（保留原类型）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolNodeHandler implements NodeHandler {

    private final ToolExecutor toolExecutor;
    private final VariableResolver variableResolver;

    @Override
    public NodeType type() {
        return NodeType.TOOL;
    }

    @Override
    public NodeExecutionResult handle(NodeDefinition node, WorkflowContext ctx) {
        if (node.getToolName() == null || node.getToolName().isBlank()) {
            return NodeExecutionResult.failure("TOOL 节点[" + node.getId() + "]未配置 toolName");
        }

        // 1. 解析入参（${var} → 实际值）
        var resolvedArgs = variableResolver.resolveArgs(node.getArguments(), ctx);

        // 2. 构建 ToolContext（身份显式传递，供权限校验与审计）
        ToolContext toolCtx = ToolContext.builder()
                .tenantId(ctx.getTenantId())
                .userId(ctx.getUserId())
                .kbId(ctx.getKbId())
                .taskId(ctx.getTaskId())
                .build();

        // 3. 委托 ToolExecutor 执行（含权限校验、审计、租户上下文、异常兜底）
        ToolResult result = toolExecutor.execute(node.getToolName(), toolCtx, resolvedArgs);

        if (!result.isSuccess()) {
            return NodeExecutionResult.failure(result.getErrorMessage());
        }
        log.info("[Workflow] TOOL 节点={} 工具={} 完成 token={}",
                node.getId(), node.getToolName(), result.getTokensUsed());
        return NodeExecutionResult.success(result.getData(), result.getTokensUsed());
    }
}
