/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.agent;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.dto.Evidence;
import com.knowledge.agent.engine.AgentContext;
import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.AgentResult;
import com.knowledge.agent.engine.ArtifactType;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.engine.LlmResult;
import com.knowledge.agent.entity.AgentTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link ReportAgent} 单元测试：验证报告生成、token 上报、分析缺失兜底。
 * <p>Mock {@link AgentLlmCaller} 直接返回 {@link LlmResult}，无需深度桩 ChatModel 调用链。
 */
@ExtendWith(MockitoExtension.class)
class ReportAgentTest {

    @Mock
    private AgentLlmCaller llmCaller;

    @Test
    void should_return_report_with_tokens_when_llm_produces_text() {
        String reportText = "# 2025销售政策变化分析报告\n\n## 摘要\n返利比例提升 [来源:2025.pdf#doc1_0]";
        when(llmCaller.call(anyString(), anyString(), anyString(), any(LlmIdentity.class)))
                .thenReturn(new LlmResult(reportText, 200, 600, 800));

        ReportAgent agent = new ReportAgent(llmCaller);
        AgentResult result = agent.execute(newContextWithAnalysis());

        assertTrue(result.isSuccess());
        assertEquals(ArtifactType.REPORT.name(), result.getArtifactKey());
        assertEquals(reportText, result.getArtifact());
        assertEquals(800, result.getTokensUsed(), "应上报 LLM 调用 token 消耗");
    }

    @Test
    void should_fail_when_no_analysis_in_context() {
        // Context 中无 ANALYSIS 产物（AnalysisAgent 未执行或未产出）
        AgentContext ctx = newContext();
        ctx.putArtifact(ArtifactType.EVIDENCES.name(), List.of(buildEvidence()));

        ReportAgent agent = new ReportAgent(llmCaller);
        AgentResult result = agent.execute(ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("缺少分析结论"));
    }

    @Test
    void should_fail_when_analysis_is_blank() {
        AgentContext ctx = newContext();
        ctx.putArtifact(ArtifactType.ANALYSIS.name(), "   ");
        ctx.putArtifact(ArtifactType.EVIDENCES.name(), List.of(buildEvidence()));

        ReportAgent agent = new ReportAgent(llmCaller);
        AgentResult result = agent.execute(ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("缺少分析结论"));
    }

    @Test
    void should_fail_when_llm_returns_blank() {
        when(llmCaller.call(anyString(), anyString(), anyString(), any(LlmIdentity.class)))
                .thenReturn(new LlmResult("   ", 2, 3, 5));

        ReportAgent agent = new ReportAgent(llmCaller);
        AgentResult result = agent.execute(newContextWithAnalysis());

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("报告生成结果为空"));
    }

    // ==================== 测试辅助 ====================

    private static AgentContext newContextWithAnalysis() {
        AgentContext ctx = newContext();
        ctx.putArtifact(ArtifactType.ANALYSIS.name(), "对比结论：2025年返利比例由3%提升至8%。");
        ctx.putArtifact(ArtifactType.EVIDENCES.name(), List.of(buildEvidence()));
        return ctx;
    }

    private static AgentContext newContext() {
        AgentTask task = new AgentTask();
        task.setId(1L);
        task.setTenantId(1L);
        task.setUserId(10L);
        task.setKbId(100L);
        task.setGoal("分析2025销售政策相比2024的变化");
        return new AgentContext(task, new AgentProperties());
    }

    private static Evidence buildEvidence() {
        Evidence e = new Evidence();
        e.setDocumentId(1L);
        e.setSource("2025.pdf");
        e.setChunkId("doc1_0");
        e.setChunkIndex(0);
        e.setContent("2025年返利政策：返利比例8%");
        e.setScore(0.9);
        e.setScoreType("fused");
        return e;
    }
}
