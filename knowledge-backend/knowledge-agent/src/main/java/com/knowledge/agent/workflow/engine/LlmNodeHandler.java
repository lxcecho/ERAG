package com.knowledge.agent.workflow.engine;

import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.engine.LlmResult;
import com.knowledge.agent.workflow.definition.NodeDefinition;
import com.knowledge.agent.workflow.enums.NodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 节点处理器：基于 prompt 模板 + 上下文变量插值，调用大模型生成文本。
 * <p>
 * 复用 Agent 层的 {@link AgentLlmCaller}（封装 ChatModel + 调用埋点），
 * 与 AnalysisAgent/ReportGenerateTool 走同一调用链，模型/温度配置统一，调用日志统一记录。
 * <p>产物为生成的文本字符串，写入上下文 outputKey 供下游引用。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeHandler implements NodeHandler {

    private final AgentLlmCaller llmCaller;
    private final VariableResolver variableResolver;

    @Override
    public NodeType type() {
        return NodeType.LLM;
    }

    @Override
    public NodeExecutionResult handle(NodeDefinition node, WorkflowContext ctx) {
        if (node.getPromptTemplate() == null || node.getPromptTemplate().isBlank()) {
            return NodeExecutionResult.failure("LLM 节点[" + node.getId() + "]未配置 promptTemplate");
        }

        // 1. 变量插值
        String userPrompt = variableResolver.resolveString(node.getPromptTemplate(), ctx);
        String systemPrompt = node.getSystemPrompt() != null
                ? variableResolver.resolveString(node.getSystemPrompt(), ctx)
                : null;

        // 2. 调用 LLM（埋点由 AgentLlmCaller 统一记录）
        LlmResult llm = llmCaller.call(systemPrompt, userPrompt, "workflow",
                LlmIdentity.of(ctx.getUserId(), ctx.getTenantId()));

        String text = llm.text();
        int tokens = llm.totalTokens();

        if (text == null || text.isBlank()) {
            return NodeExecutionResult.failure("LLM 节点[" + node.getId() + "]生成结果为空");
        }

        log.info("[Workflow] LLM 节点={} 完成 长度={} token={}", node.getId(), text.length(), tokens);
        return NodeExecutionResult.success(text, tokens);
    }
}
