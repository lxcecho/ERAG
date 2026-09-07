/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.agent;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.dto.Plan;
import com.knowledge.agent.engine.AgentContext;
import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.AgentResult;
import com.knowledge.agent.engine.ArtifactType;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.agent.memory.MemoryService;
import com.knowledge.agent.memory.dto.MemoryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlannerAgent} 单元测试：验证结构化输出解析、空结果兜底、长期记忆注入。
 * <p>Mock {@link AgentLlmCaller#callEntity} 直接返回 {@link Plan}，无需深度桩 ChatModel 调用链。
 * <p>sessionId=null 的任务跳过记忆加载；sessionId 非空时验证记忆注入到 user prompt。
 */
@ExtendWith(MockitoExtension.class)
class PlannerAgentTest {

    @Mock
    private AgentLlmCaller llmCaller;
    @Mock
    private MemoryService memoryService;

    @Test
    void should_return_plan_when_llm_produces_valid_queries() {
        Plan expected = new Plan();
        expected.setUnderstanding("对比2025与2024销售政策");
        expected.setSearchQueries(List.of("2025年销售政策", "2024年销售政策", "返利政策变化"));
        expected.setAnalysisApproach("按年度对比关键条款");

        when(llmCaller.callEntity(anyString(), anyString(), eq(Plan.class), anyString(), any(LlmIdentity.class)))
                .thenReturn(expected);

        PlannerAgent planner = new PlannerAgent(llmCaller, memoryService);
        AgentResult result = planner.execute(newContext());

        assertTrue(result.isSuccess());
        assertEquals(ArtifactType.PLAN.name(), result.getArtifactKey());
        Plan actual = (Plan) result.getArtifact();
        assertEquals(expected.getUnderstanding(), actual.getUnderstanding());
        assertEquals(3, actual.getSearchQueries().size());
        // sessionId=null → 不调用记忆加载
        verify(memoryService, never()).loadContext(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void should_fail_when_plan_has_no_queries() {
        Plan empty = new Plan();
        empty.setSearchQueries(List.of());

        when(llmCaller.callEntity(anyString(), anyString(), eq(Plan.class), anyString(), any(LlmIdentity.class)))
                .thenReturn(empty);

        PlannerAgent planner = new PlannerAgent(llmCaller, memoryService);
        AgentResult result = planner.execute(newContext());

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未生成有效检索查询"));
    }

    @Test
    void should_fail_when_llm_returns_null() {
        when(llmCaller.callEntity(anyString(), anyString(), eq(Plan.class), anyString(), any(LlmIdentity.class)))
                .thenReturn(null);

        PlannerAgent planner = new PlannerAgent(llmCaller, memoryService);
        AgentResult result = planner.execute(newContext());

        assertFalse(result.isSuccess());
    }

    @Test
    void should_inject_memory_into_prompt_when_session_id_present() {
        // sessionId 非空：加载记忆并注入到 user prompt
        MemoryContext mc = new MemoryContext();
        mc.setSummary("用户关注 RAG 架构");
        mc.setLongTermFacts(List.of("偏好 Java17"));
        when(memoryService.loadContext(eq(200L), anyLong(), anyLong(), anyString())).thenReturn(mc);

        Plan plan = new Plan();
        plan.setUnderstanding("理解目标");
        plan.setSearchQueries(List.of("查询1"));
        plan.setAnalysisApproach("方向");
        when(llmCaller.callEntity(anyString(), anyString(), eq(Plan.class), anyString(), any(LlmIdentity.class)))
                .thenAnswer(inv -> {
                    // 验证 user prompt 包含记忆背景
                    String userPrompt = inv.getArgument(1);
                    assertTrue(userPrompt.contains("用户记忆/历史背景"));
                    assertTrue(userPrompt.contains("用户关注 RAG 架构"));
                    assertTrue(userPrompt.contains("偏好 Java17"));
                    return plan;
                });

        PlannerAgent planner = new PlannerAgent(llmCaller, memoryService);
        AgentResult result = planner.execute(newContextWithSession());

        assertTrue(result.isSuccess());
        verify(memoryService).loadContext(eq(200L), anyLong(), anyLong(), anyString());
    }

    @Test
    void should_degrade_gracefully_when_memory_load_fails() {
        // 记忆加载抛异常 → 降级无记忆，规划仍正常进行
        when(memoryService.loadContext(anyLong(), anyLong(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("记忆服务不可用"));

        Plan plan = new Plan();
        plan.setUnderstanding("理解");
        plan.setSearchQueries(List.of("查询1"));
        plan.setAnalysisApproach("方向");
        when(llmCaller.callEntity(anyString(), anyString(), eq(Plan.class), anyString(), any(LlmIdentity.class)))
                .thenReturn(plan);

        PlannerAgent planner = new PlannerAgent(llmCaller, memoryService);
        AgentResult result = planner.execute(newContextWithSession());

        // 降级不阻断规划
        assertTrue(result.isSuccess());
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

    private static AgentContext newContextWithSession() {
        AgentTask task = new AgentTask();
        task.setId(2L);
        task.setTenantId(1L);
        task.setUserId(10L);
        task.setKbId(100L);
        task.setSessionId(200L);
        task.setGoal("分析2025销售政策相比2024的变化");
        return new AgentContext(task, new AgentProperties());
    }
}
