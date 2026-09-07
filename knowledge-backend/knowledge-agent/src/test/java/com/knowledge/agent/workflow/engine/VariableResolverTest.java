/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.workflow.entity.WorkflowTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VariableResolver} 单元测试：覆盖模板插值、整体引用保类型、缺失变量容错。
 */
class VariableResolverTest {

    private VariableResolver resolver;
    private WorkflowContext ctx;

    @BeforeEach
    void setUp() {
        resolver = new VariableResolver(new ObjectMapper());
        WorkflowTask task = new WorkflowTask();
        task.setId(1L);
        task.setTenantId(1L);
        task.setUserId(10L);
        task.setDefinitionId(100L);
        task.setGoal("分析2025销售政策");
        ctx = new WorkflowContext(task);
        ctx.setVariable("goal", "分析2025销售政策");
        ctx.setVariable("summary", "核心结论：政策向新客倾斜");
        ctx.setVariable("evidences", List.of(Map.of("source", "policy.pdf", "text", "新客立减")));
    }

    @Test
    void should_return_unchanged_when_no_placeholder() {
        assertEquals("普通文本", resolver.resolveString("普通文本", ctx));
    }

    @Test
    void should_resolve_single_variable_in_template() {
        String tpl = "目标：${goal}";
        assertEquals("目标：分析2025销售政策", resolver.resolveString(tpl, ctx));
    }

    @Test
    void should_resolve_multiple_variables_in_template() {
        String tpl = "目标：${goal}；结论：${summary}";
        assertEquals("目标：分析2025销售政策；结论：核心结论：政策向新客倾斜",
                resolver.resolveString(tpl, ctx));
    }

    @Test
    void should_serialize_complex_variable_to_json() {
        String result = resolver.resolveString("${evidences}", ctx);
        assertTrue(result.contains("policy.pdf"), "结构化变量应序列化为 JSON");
        assertTrue(result.contains("新客立减"));
    }

    @Test
    void should_resolve_missing_variable_to_empty() {
        assertEquals("值：", resolver.resolveString("值：${notExist}", ctx));
    }

    @Test
    void resolveArgs_should_preserve_original_type_for_single_reference() {
        Map<String, Object> args = Map.of("content", "${summary}");
        Object resolved = resolver.resolveArgs(args, ctx).get("content");
        assertEquals("核心结论：政策向新客倾斜", resolved);
    }

    @Test
    void resolveArgs_should_return_raw_object_for_single_structured_reference() {
        Map<String, Object> args = Map.of("evidences", "${evidences}");
        Object resolved = resolver.resolveArgs(args, ctx).get("evidences");
        assertTrue(resolved instanceof List, "整体引用结构化变量应保留原 List 类型");
    }

    @Test
    void resolveArgs_should_keep_literal_number_as_is() {
        Map<String, Object> args = Map.of("topK", 8);
        assertEquals(8, resolver.resolveArgs(args, ctx).get("topK"));
    }

    @Test
    void resolveArgs_should_interpolate_embedded_string() {
        Map<String, Object> args = Map.of("q", "查询：${goal}");
        assertEquals("查询：分析2025销售政策", resolver.resolveArgs(args, ctx).get("q"));
    }

    @Test
    void resolveArgs_should_handle_null_args() {
        assertTrue(resolver.resolveArgs(null, ctx).isEmpty());
    }

    @Test
    void resolveArgs_single_ref_to_missing_var_returns_null() {
        Map<String, Object> args = Map.of("x", "${notExist}");
        assertNull(resolver.resolveArgs(args, ctx).get("x"));
    }
}
