/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.agent;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.dto.Evidence;
import com.knowledge.agent.dto.Plan;
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
 * {@link AnalysisAgent} 单元测试：验证 LLM 分析产出、token 上报、证据缺失兜底。
 * <p>Mock {@link AgentLlmCaller}，直接返回 {@link LlmResult}，无需深度桩 ChatModel 调用链。
 */
@ExtendWith(MockitoExtension.class)
class AnalysisAgentTest {

    @Mock
    private AgentLlmCaller llmCaller;

    @Test
    void should_return_analysis_with_tokens_when_llm_produces_text() {
        when(llmCaller.call(anyString(), anyString(), anyString(), any(LlmIdentity.class)))
                .thenReturn(new LlmResult("对比结论：2025年返利比例由3%提升至8%。", 100, 400, 500));

        AnalysisAgent agent = new AnalysisAgent(llmCaller);
        AgentResult result = agent.execute(newContextWithEvidences());

        assertTrue(result.isSuccess());
        assertEquals(ArtifactType.ANALYSIS.name(), result.getArtifactKey());
        assertEquals("对比结论：2025年返利比例由3%提升至8%。", result.getArtifact());
        assertEquals(500, result.getTokensUsed(), "应上报 LLM 调用 token 消耗");
    }

    @Test
    void should_fail_when_no_evidence_in_context() {
        // Context 中无 EVIDENCES 产物（KnowledgeAgent 未执行或未召回）
        AgentContext ctx = newContext();
        AnalysisAgent agent = new AnalysisAgent(llmCaller);

        AgentResult result = agent.execute(ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("缺少证据"));
    }

    @Test
    void should_fail_when_evidence_list_is_empty() {
        AgentContext ctx = newContext();
        ctx.putArtifact(ArtifactType.EVIDENCES.name(), List.of());

        AnalysisAgent agent = new AnalysisAgent(llmCaller);
        AgentResult result = agent.execute(ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("缺少证据"));
    }

    @Test
    void should_fail_when_llm_returns_blank() {
        when(llmCaller.call(anyString(), anyString(), anyString(), any(LlmIdentity.class)))
                .thenReturn(new LlmResult("   ", 2, 8, 10));

        AnalysisAgent agent = new AnalysisAgent(llmCaller);
        AgentResult result = agent.execute(newContextWithEvidences());

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("分析结果为空"));
    }

    // ==================== 测试辅助 ====================

    private static AgentContext newContextWithEvidences() {
        AgentContext ctx = newContext();
        List<Evidence> evidences = List.of(
                buildEvidence(1L, "2025.pdf", "doc1_0", "2025年返利政策：返利比例8%"),
                buildEvidence(2L, "2024.pdf", "doc2_0", "2024年返利政策：返利比例3%")
        );
        ctx.putArtifact(ArtifactType.EVIDENCES.name(), evidences);

        Plan plan = new Plan();
        plan.setUnderstanding("对比2025与2024销售政策");
        plan.setSearchQueries(List.of("2025销售政策", "2024销售政策"));
        plan.setAnalysisApproach("按年度对比关键条款变化");
        ctx.putArtifact(ArtifactType.PLAN.name(), plan);
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

    private static Evidence buildEvidence(Long docId, String source, String chunkId, String content) {
        Evidence e = new Evidence();
        e.setDocumentId(docId);
        e.setSource(source);
        e.setChunkId(chunkId);
        e.setChunkIndex(0);
        e.setContent(content);
        e.setScore(0.9);
        e.setScoreType("fused");
        return e;
    }
}
