/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.entity.AgentTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolExecutor} 单元测试：验证工具调用完整生命周期编排。
 * <p>覆盖：工具不存在 / 参数缺失 / 权限拒绝 / 成功执行 / 异常捕获 / 审计记录 / 租户上下文清理。
 */
@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock
    private ToolRegistry registry;
    @Mock
    private ToolPermissionChecker permissionChecker;
    @Mock
    private ToolCallRecorder recorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SCHEMA = """
            {"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""";

    @AfterEach
    void tearDown() {
        com.knowledge.common.context.TenantContext.clear();
    }

    @Test
    void should_fail_when_tool_not_found() {
        ToolExecutor executor = new ToolExecutor(registry, permissionChecker, recorder, objectMapper);
        ToolContext ctx = newContext();

        ToolResult result = executor.execute("nonexistent", ctx, Map.of("query", "test"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("工具不存在"));
        verify(recorder, never()).record(any(), anyString(), anyMap(), any(), anyLong());
    }

    @Test
    void should_fail_when_missing_required_parameter() {
        Tool tool = fakeTool("test_tool", true, ToolResult.success("ok"));
        when(registry.getTool("test_tool")).thenReturn(tool);

        ToolExecutor executor = new ToolExecutor(registry, permissionChecker, recorder, objectMapper);

        ToolResult result = executor.execute("test_tool", newContext(), Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("缺少必填参数"));
        // 参数校验失败不应执行工具、不应记录审计
        verify(permissionChecker, never()).check(any(), any());
        verify(recorder, never()).record(any(), anyString(), anyMap(), any(), anyLong());
    }

    @Test
    void should_fail_when_permission_denied() {
        Tool tool = fakeTool("test_tool", true, ToolResult.success("ok"));
        when(registry.getTool("test_tool")).thenReturn(tool);
        doThrow(new ToolException("test_tool", "PERMISSION_DENIED", "无权限"))
                .when(permissionChecker).check(any(), any());

        ToolExecutor executor = new ToolExecutor(registry, permissionChecker, recorder, objectMapper);

        ToolResult result = executor.execute("test_tool", newContext(), Map.of("query", "test"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("无权限"));
        // 权限失败不应执行工具，但也不应记录审计（未到达执行阶段）
        verify(recorder, never()).record(any(), anyString(), anyMap(), any(), anyLong());
    }

    @Test
    void should_execute_and_record_when_successful() {
        Tool tool = fakeTool("test_tool", true, ToolResult.success("result-data", 200));
        when(registry.getTool("test_tool")).thenReturn(tool);

        ToolExecutor executor = new ToolExecutor(registry, permissionChecker, recorder, objectMapper);
        ToolContext ctx = newContext();

        ToolResult result = executor.execute("test_tool", ctx, Map.of("query", "test"));

        assertTrue(result.isSuccess());
        assertEquals("result-data", result.getData());
        assertEquals(200, result.getTokensUsed());
        verify(permissionChecker).check(tool, ctx);
        // 审计记录必须被调用
        verify(recorder).record(eq(ctx), eq("test_tool"), anyMap(), any(ToolResult.class), anyLong());
        // 执行后租户上下文应被清理
        assertNull(com.knowledge.common.context.TenantContext.getTenantId(),
                "执行后 TenantContext 必须清理");
    }

    @Test
    void should_catch_exception_and_return_failure() {
        Tool tool = fakeThrowingTool("test_tool", new RuntimeException("LLM 超时"));
        when(registry.getTool("test_tool")).thenReturn(tool);

        ToolExecutor executor = new ToolExecutor(registry, permissionChecker, recorder, objectMapper);

        ToolResult result = executor.execute("test_tool", newContext(), Map.of("query", "test"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("LLM 超时"));
        // 异常也应记录审计（便于排查）
        verify(recorder).record(any(), eq("test_tool"), anyMap(), any(ToolResult.class), anyLong());
        assertNull(com.knowledge.common.context.TenantContext.getTenantId());
    }

    @Test
    void should_clear_tenant_context_even_on_failure() {
        Tool tool = fakeThrowingTool("test_tool", new RuntimeException("boom"));
        when(registry.getTool("test_tool")).thenReturn(tool);

        ToolExecutor executor = new ToolExecutor(registry, permissionChecker, recorder, objectMapper);
        executor.execute("test_tool", newContext(), Map.of("query", "test"));

        assertNull(com.knowledge.common.context.TenantContext.getTenantId(),
                "异常路径也必须清理 TenantContext，防止线程池复用串租户");
    }

    // ==================== 测试辅助 ====================

    private static ToolContext newContext() {
        AgentTask task = new AgentTask();
        task.setId(1L);
        task.setTenantId(1L);
        task.setUserId(10L);
        task.setKbId(100L);
        return ToolContext.of(task, null);
    }

    private static Tool fakeTool(String name, boolean authRequired, ToolResult result) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "test tool"; }
            @Override public String parametersJsonSchema() { return SCHEMA; }
            @Override public boolean authRequired() { return authRequired; }
            @Override public ToolResult execute(ToolContext ctx, Map<String, Object> args) { return result; }
        };
    }

    private static Tool fakeThrowingTool(String name, RuntimeException ex) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "test tool"; }
            @Override public String parametersJsonSchema() { return SCHEMA; }
            @Override public boolean authRequired() { return true; }
            @Override public ToolResult execute(ToolContext ctx, Map<String, Object> args) { throw ex; }
        };
    }
}
