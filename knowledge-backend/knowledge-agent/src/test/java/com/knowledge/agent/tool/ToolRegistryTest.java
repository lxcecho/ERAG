/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ToolRegistry} 单元测试：验证 SPI 自动注册、按名查找、列表输出。
 */
class ToolRegistryTest {

    @Test
    void should_register_and_lookup_tools_by_name() {
        Tool a = fakeTool("tool_a");
        Tool b = fakeTool("tool_b");
        ToolRegistry registry = new ToolRegistry(List.of(a, b));

        assertEquals(a, registry.getTool("tool_a"));
        assertEquals(b, registry.getTool("tool_b"));
        assertTrue(registry.exists("tool_a"));
        assertFalse(registry.exists("nonexistent"));
    }

    @Test
    void should_return_null_for_unknown_tool() {
        ToolRegistry registry = new ToolRegistry(List.of());
        assertNull(registry.getTool("nonexistent"));
    }

    @Test
    void should_list_all_registered_tools() {
        Tool a = fakeTool("tool_a");
        Tool b = fakeTool("tool_b");
        ToolRegistry registry = new ToolRegistry(List.of(a, b));

        List<Tool> tools = registry.listTools();
        assertEquals(2, tools.size());
        assertNotNull(tools.get(0));
    }

    @Test
    void should_handle_empty_tool_list() {
        ToolRegistry registry = new ToolRegistry(List.of());
        assertTrue(registry.listTools().isEmpty());
    }

    // ==================== 测试辅助 ====================

    private static Tool fakeTool(String name) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "fake"; }
            @Override public String parametersJsonSchema() {
                return "{\"type\":\"object\",\"properties\":{}}";
            }
            @Override public boolean authRequired() { return false; }
            @Override public ToolResult execute(ToolContext ctx, Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
