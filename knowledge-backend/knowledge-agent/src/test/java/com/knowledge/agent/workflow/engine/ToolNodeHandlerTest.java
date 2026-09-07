/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.workflow.engine;

import com.knowledge.agent.tool.ToolExecutor;
import com.knowledge.agent.tool.ToolResult;
import com.knowledge.agent.workflow.definition.NodeDefinition;
import com.knowledge.agent.workflow.entity.WorkflowTask;
import com.knowledge.agent.workflow.enums.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link ToolNodeHandler} 单元测试：验证委托 ToolExecutor + 结果映射。
 */
@ExtendWith(MockitoExtension.class)
class ToolNodeHandlerTest {

    @Mock
    private ToolExecutor toolExecutor;
    @Mock
    private VariableResolver variableResolver;

    private ToolNodeHandler handler;
    private WorkflowContext ctx;

    @BeforeEach
    void setUp() {
        handler = new ToolNodeHandler(toolExecutor, variableResolver);
        WorkflowTask task = new WorkflowTask();
        task.setId(1L);
        task.setTenantId(1L);
        task.setUserId(10L);
        task.setKbId(100L);
        task.setDefinitionId(50L);
        ctx = new WorkflowContext(task);
    }

    @Test
    void should_return_tool_type() {
        assertEquals(NodeType.TOOL, handler.type());
    }

    @Test
    void should_map_success_result_with_tokens() {
        NodeDefinition node = toolNode("knowledge_search", Map.of("query", "${goal}"));
        when(variableResolver.resolveArgs(any(), any())).thenReturn(Map.of("query", "分析政策"));
        when(toolExecutor.execute(eq("knowledge_search"), any(), any()))
                .thenReturn(ToolResult.success("证据列表", 120));

        NodeExecutionResult r = handler.handle(node, ctx);

        assertTrue(r.isSuccess());
        assertEquals("证据列表", r.getOutput());
        assertEquals(120, r.getTokensUsed());
    }

    @Test
    void should_map_failure_result() {
        NodeDefinition node = toolNode("knowledge_search", Map.of());
        when(variableResolver.resolveArgs(any(), any())).thenReturn(Map.of());
        when(toolExecutor.execute(eq("knowledge_search"), any(), any()))
                .thenReturn(ToolResult.failure("未检索到资料"));

        NodeExecutionResult r = handler.handle(node, ctx);

        assertFalse(r.isSuccess());
        assertEquals("未检索到资料", r.getErrorMessage());
    }

    @Test
    void should_fail_when_tool_name_missing() {
        NodeDefinition node = toolNode(null, Map.of());
        NodeExecutionResult r = handler.handle(node, ctx);

        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage().contains("未配置 toolName"));
    }

    private NodeDefinition toolNode(String toolName, Map<String, Object> args) {
        NodeDefinition n = new NodeDefinition();
        n.setId("n1");
        n.setName("节点1");
        n.setType(NodeType.TOOL);
        n.setToolName(toolName);
        n.setArguments(args);
        n.setOutputKey("out");
        return n;
    }
}
