/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool;

import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.engine.LlmResult;
import com.knowledge.agent.entity.AgentTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link ReportGenerateTool} 单元测试：验证 LLM 报告生成与 token 上报。
 * <p>Mock {@link AgentLlmCaller} 直接返回 {@link LlmResult}，无需深度桩 ChatModel 调用链。
 */
@ExtendWith(MockitoExtension.class)
class ReportGenerateToolTest {

    @Mock
    private AgentLlmCaller llmCaller;

    @Test
    void should_generate_report_with_tokens() {
        String reportText = "# 销售政策分析报告\n\n## 摘要\n2025年返利比例提升。";
        when(llmCaller.call(anyString(), anyString(), anyString(), any(LlmIdentity.class)))
                .thenReturn(new LlmResult(reportText, 100, 500, 600));

        ReportGenerateTool tool = new ReportGenerateTool(llmCaller);
        ToolResult result = tool.execute(newContext(), Map.of(
                "topic", "2025年Q3销售政策分析",
                "content", "返利比例从3%提升至8%，覆盖所有区域。"));

        assertTrue(result.isSuccess());
        assertEquals(reportText, result.getData());
        assertEquals(600, result.getTokensUsed());
    }

    @Test
    void should_fail_when_llm_returns_blank() {
        when(llmCaller.call(anyString(), anyString(), anyString(), any(LlmIdentity.class)))
                .thenReturn(new LlmResult("   ", 2, 3, 5));

        ReportGenerateTool tool = new ReportGenerateTool(llmCaller);
        ToolResult result = tool.execute(newContext(), Map.of(
                "topic", "测试",
                "content", "测试内容"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("报告生成结果为空"));
    }

    @Test
    void should_use_default_format_when_not_specified() {
        when(llmCaller.call(anyString(), anyString(), anyString(), any(LlmIdentity.class)))
                .thenReturn(new LlmResult("# 报告", 20, 80, 100));

        ReportGenerateTool tool = new ReportGenerateTool(llmCaller);
        ToolResult result = tool.execute(newContext(), Map.of(
                "topic", "测试",
                "content", "内容"));

        assertTrue(result.isSuccess());
    }

    @Test
    void should_not_require_auth() {
        ReportGenerateTool tool = new ReportGenerateTool(llmCaller);
        assertFalse(tool.authRequired(), "报告生成工具不接触 KB 数据，无需权限校验");
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
}
