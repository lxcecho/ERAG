/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.engine;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.entity.AgentStep;
import com.knowledge.agent.entity.AgentTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentExecutor} 单元测试：验证流水线编排、失败终止、产物传递。
 */
@ExtendWith(MockitoExtension.class)
class AgentExecutorTest {

    @Mock
    private AgentTaskManager taskManager;

    private AgentProperties props;
    private AgentTask task;

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        task = new AgentTask();
        task.setId(1001L);
        task.setTenantId(1L);
        task.setUserId(10L);
        task.setKbId(100L);
        task.setGoal("分析销售政策变化");
        task.setStatus(AgentStatus.CREATED.name());

        when(taskManager.getById(1001L)).thenReturn(task);
        when(taskManager.startStep(anyLong(), anyLong(), anyInt(), any(AgentType.class), anyString()))
                .thenAnswer(inv -> {
                    AgentStep s = new AgentStep();
                    s.setId(System.nanoTime());
                    return s;
                });

        // 真实 AgentTaskManager 会在 startTask/completeTask/failTask 持久化状态后，
        // 由 getById 再读回最新状态。Mock 默认 void 方法为 no-op，会导致 task.status 永不流转，
        // 故用 lenient doAnswer 同步变更 task 对象，使 getById 返回值反映终态。
        // 用 lenient：不同测试用例触发的终态方法不同（完成用例不调 failTask，失败用例不调 completeTask），
        // 避免 STRICT_STUBS 误报 UnnecessaryStubbing。
        lenient().doAnswer(inv -> {
                    task.setStatus(AgentStatus.EXECUTING.name());
                    return null;
                }).when(taskManager).startTask(anyLong());
        lenient().doAnswer(inv -> {
                    task.setStatus(AgentStatus.COMPLETED.name());
                    task.setResult(inv.getArgument(1, String.class));
                    return null;
                }).when(taskManager).completeTask(anyLong(), anyString());
        lenient().doAnswer(inv -> {
                    task.setStatus(AgentStatus.FAILED.name());
                    task.setErrorCode(inv.getArgument(1, String.class));
                    task.setErrorMsg(inv.getArgument(2, String.class));
                    return null;
                }).when(taskManager).failTask(anyLong(), anyString(), anyString());
    }

    @Test
    void should_run_pipeline_and_complete_when_all_succeed() {
        // 两个 Agent 全成功
        Agent a = fakeAgent(AgentType.PLANNER, AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok"));
        Agent b = fakeAgent(AgentType.REPORT, AgentResult.success(ArtifactType.REPORT.name(), "report", "done"));
        AgentExecutor executor = new AgentExecutor(java.util.List.of(a, b), taskManager, props);

        AgentTask result = executor.execute(1001L);

        assertEquals(AgentStatus.COMPLETED.name(), result.getStatus());
        verify(taskManager, times(2)).startStep(anyLong(), anyLong(), anyInt(), any(AgentType.class), anyString());
        verify(taskManager, times(2)).successStep(anyLong(), anyString(), anyInt(), anyLong());
        verify(taskManager).completeTask(1001L, "report");
    }

    @Test
    void should_stop_and_fail_when_step_fails() {
        // 第一个成功，第二个失败
        Agent a = fakeAgent(AgentType.PLANNER, AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok"));
        Agent b = fakeAgent(AgentType.KNOWLEDGE, AgentResult.failure("未检索到资料"));
        AgentExecutor executor = new AgentExecutor(java.util.List.of(a, b), taskManager, props);

        AgentTask result = executor.execute(1001L);

        assertEquals(AgentStatus.FAILED.name(), result.getStatus());
        // 只成功 1 步（a），b 失败
        verify(taskManager, times(1)).successStep(anyLong(), anyString(), anyInt(), anyLong());
        verify(taskManager, times(1)).failStep(anyLong(), anyString());
        verify(taskManager).failTask(eqTaskId(), anyString(), anyString());
    }

    @Test
    void should_mark_failed_on_exception() {
        Agent a = fakeAgentThrowing(AgentType.PLANNER, new RuntimeException("LLM 超时"));
        AgentExecutor executor = new AgentExecutor(java.util.List.of(a), taskManager, props);

        AgentTask result = executor.execute(1001L);

        assertEquals(AgentStatus.FAILED.name(), result.getStatus());
        verify(taskManager).failStep(anyLong(), anyString());
        verify(taskManager).failTask(eqTaskId(), anyString(), anyString());
    }

    @Test
    void should_propagate_artifact_between_agents() {
        // 验证 a 产出的 PLAN 能被 b 读到
        final java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        Agent a = fakeAgent(AgentType.PLANNER, AgentResult.success(ArtifactType.PLAN.name(), "my-plan", "ok"));
        Agent b = new Agent() {
            @Override
            public AgentType type() { return AgentType.ANALYSIS; }
            @Override
            public AgentResult execute(AgentContext ctx) {
                seen.set(ctx.getArtifact(ArtifactType.PLAN.name()));
                return AgentResult.success(ArtifactType.ANALYSIS.name(), "analysis", "ok");
            }
        };
        AgentExecutor executor = new AgentExecutor(java.util.List.of(a, b), taskManager, props);

        executor.execute(1001L);

        assertEquals("my-plan", seen.get(), "b 应能读到 a 写入 Context 的产物");
    }

    @Test
    void should_fail_when_token_budget_exceeded() {
        // 设置极小 token 预算，单步 LLM 消耗即超限
        props.setMaxTokensPerTask(100);
        Agent a = fakeAgent(AgentType.PLANNER,
                AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok", 150));
        AgentExecutor executor = new AgentExecutor(java.util.List.of(a), taskManager, props);

        AgentTask result = executor.execute(1001L);

        assertEquals(AgentStatus.FAILED.name(), result.getStatus());
        assertEquals("TOKEN_BUDGET_EXCEEDED", result.getErrorCode());
        // 超限时应回写 token 用量
        verify(taskManager).updateTokenUsage(eq(1001L), eq(150));
    }

    @Test
    void should_fail_when_max_steps_exceeded() {
        // 只允许 1 步，第 2 个 Agent 触发步数预算终止
        props.setMaxSteps(1);
        Agent a = fakeAgent(AgentType.PLANNER, AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok"));
        Agent b = fakeAgent(AgentType.KNOWLEDGE,
                AgentResult.success(ArtifactType.EVIDENCES.name(), java.util.List.of(), "ok"));
        AgentExecutor executor = new AgentExecutor(java.util.List.of(a, b), taskManager, props);

        AgentTask result = executor.execute(1001L);

        assertEquals(AgentStatus.FAILED.name(), result.getStatus());
        assertEquals("MAX_STEPS", result.getErrorCode());
        // 只执行了第 1 步（a），b 因步数预算终止未执行
        verify(taskManager, times(1)).startStep(anyLong(), anyLong(), anyInt(),
                any(AgentType.class), anyString());
        verify(taskManager).failTask(eq(1001L), eq("MAX_STEPS"), anyString());
    }

    // ==================== 测试辅助 ====================

    private static Agent fakeAgent(AgentType type, AgentResult result) {
        return new Agent() {
            @Override
            public AgentType type() { return type; }
            @Override
            public AgentResult execute(AgentContext ctx) { return result; }
        };
    }

    private static Agent fakeAgentThrowing(AgentType type, RuntimeException ex) {
        return new Agent() {
            @Override
            public AgentType type() { return type; }
            @Override
            public AgentResult execute(AgentContext ctx) { throw ex; }
        };
    }

    private static Long eqTaskId() {
        return org.mockito.ArgumentMatchers.eq(1001L);
    }
}
