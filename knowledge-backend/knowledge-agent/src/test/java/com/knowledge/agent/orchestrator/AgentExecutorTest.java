/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.orchestrator;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.engine.Agent;
import com.knowledge.agent.engine.AgentContext;
import com.knowledge.agent.engine.AgentResult;
import com.knowledge.agent.engine.AgentType;
import com.knowledge.agent.engine.ArtifactType;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.agent.orchestrator.entity.AgentCompensation;
import com.knowledge.agent.orchestrator.entity.AgentNodeRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentExecutor} 单元测试：验证图编排、节点重试、硬超时、Saga 补偿回滚、回滚开关、下游跳过。
 * <p>手动调用 {@code init()} 触发默认图构建（@PostConstruct 在纯单测不自动执行）。
 */
@ExtendWith(MockitoExtension.class)
class AgentExecutorTest {

    @Mock
    private OrchestratorTaskManager taskManager;

    private AgentProperties props;
    private AgentTask task;
    private ExecutorService pool;
    private final AtomicLong runIdSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        // 测试用：无节点级超时（超时测试单独构造图）、快速退避、1 次重试、开启回滚
        props.getOrchestrator().setDefaultNodeTimeoutMs(0L);
        props.getOrchestrator().setDefaultRetryBackoffMs(10L);
        props.getOrchestrator().setDefaultMaxRetries(1);
        props.getOrchestrator().setRollbackEnabled(true);

        task = new AgentTask();
        task.setId(1001L);
        task.setTenantId(1L);
        task.setUserId(10L);
        task.setKbId(100L);
        task.setGoal("分析销售政策变化");
        task.setStatus(OrchestratorStatus.CREATED.name());

        pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "orch-test");
            t.setDaemon(true);
            return t;
        });
        runIdSeq.set(1);

        when(taskManager.getById(1001L)).thenReturn(task);
        when(taskManager.startNodeRun(anyLong(), anyLong(), anyString(), any(),
                anyString(), anyInt(), anyInt(), anyLong(), anyString()))
                .thenAnswer(inv -> {
                    AgentNodeRun r = new AgentNodeRun();
                    r.setId(runIdSeq.getAndIncrement());
                    return r;
                });
        lenient().when(taskManager.startCompensation(anyLong(), anyLong(), anyString(), any(), anyString()))
                .thenAnswer(inv -> {
                    AgentCompensation c = new AgentCompensation();
                    c.setId(runIdSeq.getAndIncrement());
                    return c;
                });

        // 同步流转 task.status，使 getById 返回值反映终态
        lenient().doAnswer(inv -> {
                    task.setStatus(OrchestratorStatus.RUNNING.name());
                    return null;
                }).when(taskManager).updateStatus(anyLong(), any(OrchestratorStatus.class));
        lenient().doAnswer(inv -> {
                    task.setStatus(OrchestratorStatus.COMPLETED.name());
                    task.setResult(inv.getArgument(1, String.class));
                    return null;
                }).when(taskManager).completeTask(anyLong(), anyString());
        lenient().doAnswer(inv -> {
                    task.setStatus(OrchestratorStatus.FAILED.name());
                    task.setErrorCode(inv.getArgument(1, String.class));
                    task.setErrorMsg(inv.getArgument(2, String.class));
                    return null;
                }).when(taskManager).failTask(anyLong(), anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void should_complete_when_all_nodes_succeed() {
        Agent a = fakeAgent(AgentType.PLANNER,
                AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok"));
        Agent b = fakeAgent(AgentType.REPORT,
                AgentResult.success(ArtifactType.REPORT.name(), "report", "done"));
        AgentExecutor executor = newExecutor(a, b);

        AgentTask result = executor.execute(1001L);

        assertEquals(OrchestratorStatus.COMPLETED.name(), result.getStatus());
        verify(taskManager).completeTask(eq(1001L), eq("report"));
        verify(taskManager, times(2)).successNodeRun(anyLong(), anyString(), anyInt(), anyLong());
    }

    @Test
    void should_retry_then_succeed() {
        // 首次失败，第二次成功
        AtomicInteger calls = new AtomicInteger(0);
        Agent a = new Agent() {
            @Override
            public AgentType type() { return AgentType.PLANNER; }
            @Override
            public AgentResult execute(AgentContext ctx) {
                if (calls.incrementAndGet() == 1) {
                    return AgentResult.failure("瞬时错误");
                }
                return AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok");
            }
        };
        AgentExecutor executor = newExecutor(a);

        AgentTask result = executor.execute(1001L);

        assertEquals(OrchestratorStatus.COMPLETED.name(), result.getStatus());
        assertEquals(2, calls.get(), "应执行 2 次（首次失败 + 重试成功）");
        verify(taskManager, times(1)).retryNodeRun(anyLong(), anyString());
        verify(taskManager, times(1)).successNodeRun(anyLong(), anyString(), anyInt(), anyLong());
    }

    @Test
    void should_fail_and_rollback_when_retries_exhausted() {
        AtomicBoolean compensated = new AtomicBoolean(false);
        // 上游成功且可补偿
        FakeCompensableAgent a = new FakeCompensableAgent(AgentType.PLANNER,
                AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok"), compensated);
        // 下游始终失败
        Agent b = fakeAgent(AgentType.REPORT, AgentResult.failure("报告生成失败"));
        AgentExecutor executor = newExecutor(a, b);

        AgentTask result = executor.execute(1001L);

        assertEquals(OrchestratorStatus.FAILED.name(), result.getStatus());
        assertTrue(compensated.get(), "上游 Compensable 节点应被补偿");
        verify(taskManager).startCompensation(eq(1L), eq(1001L), eq("planner"), any(), anyString());
        verify(taskManager).successCompensation(anyLong(), anyLong());
        verify(taskManager).failTask(eq(1001L), eq("NODE_FAILED"), anyString());
    }

    @Test
    void should_timeout_when_node_exceeds_hard_timeout() {
        // 慢 Agent：sleep 1000ms，节点超时 100ms
        Agent slow = new Agent() {
            @Override
            public AgentType type() { return AgentType.PLANNER; }
            @Override
            public AgentResult execute(AgentContext ctx) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return AgentResult.failure("被中断");
                }
                return AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok");
            }
        };
        AgentExecutor executor = newExecutor(slow);
        // 自定义图：单节点，硬超时 100ms，不重试
        AgentGraph graph = AgentGraph.builder()
                .code("timeout-test")
                .node(AgentNode.builder()
                        .nodeId("planner").agentType(AgentType.PLANNER).name("planner")
                        .maxRetries(0).retryBackoffMs(0L).timeoutMs(100L).build())
                .build();

        AgentTask result = executor.execute(1001L, graph);

        assertEquals(OrchestratorStatus.FAILED.name(), result.getStatus());
        verify(taskManager).timeoutNodeRun(anyLong(), anyString(), anyLong());
        verify(taskManager).failTask(eq(1001L), eq("NODE_FAILED"), anyString());
    }

    @Test
    void should_skip_downstream_nodes_on_failure() {
        Agent a = fakeAgent(AgentType.PLANNER, AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok"));
        Agent b = fakeAgent(AgentType.KNOWLEDGE, AgentResult.failure("检索失败"));
        Agent c = fakeAgent(AgentType.REPORT, AgentResult.success(ArtifactType.REPORT.name(), "r", "ok"));
        AgentExecutor executor = newExecutor(a, b, c);

        AgentTask result = executor.execute(1001L);

        assertEquals(OrchestratorStatus.FAILED.name(), result.getStatus());
        // c 未执行，应记一条 SKIPPED
        verify(taskManager).skipNodeRun(eq(1L), eq(1001L), eq("report"), any(), anyString(), anyInt());
    }

    @Test
    void should_not_rollback_when_disabled() {
        props.getOrchestrator().setRollbackEnabled(false);
        Agent a = fakeAgent(AgentType.PLANNER, AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok"));
        Agent b = fakeAgent(AgentType.REPORT, AgentResult.failure("报告失败"));
        AgentExecutor executor = newExecutor(a, b);

        AgentTask result = executor.execute(1001L);

        assertEquals(OrchestratorStatus.FAILED.name(), result.getStatus());
        verify(taskManager, never()).startCompensation(anyLong(), anyLong(), anyString(), any(), anyString());
    }

    @Test
    void should_keep_rollback_chain_on_compensation_failure() {
        // 上游两个成功节点（均可补偿），下游失败；第一个补偿抛异常，第二个仍应执行（best-effort）
        AtomicBoolean comp1 = new AtomicBoolean(false);
        AtomicBoolean comp2 = new AtomicBoolean(false);
        FakeCompensableAgent a = new FakeCompensableAgent(AgentType.PLANNER,
                AgentResult.success(ArtifactType.PLAN.name(), "plan", "ok"), comp1, true);
        FakeCompensableAgent b = new FakeCompensableAgent(AgentType.KNOWLEDGE,
                AgentResult.success(ArtifactType.EVIDENCES.name(), List.of(), "ok"), comp2, false);
        Agent c = fakeAgent(AgentType.REPORT, AgentResult.failure("报告失败"));
        AgentExecutor executor = newExecutor(a, b, c);

        AgentTask result = executor.execute(1001L);

        assertEquals(OrchestratorStatus.FAILED.name(), result.getStatus());
        assertTrue(comp1.get(), "第一个补偿应被调用（即使抛异常）");
        assertTrue(comp2.get(), "第二个补偿应被调用（best-effort，不阻断链）");
        verify(taskManager).failCompensation(anyLong(), anyString(), anyLong());
        verify(taskManager).successCompensation(anyLong(), anyLong());
    }

    // ==================== 测试辅助 ====================

    private AgentExecutor newExecutor(Agent... agents) {
        AgentExecutor executor = new AgentExecutor(List.of(agents), taskManager, props, pool);
        executor.init(); // 手动触发 @PostConstruct 逻辑（构建 agentMap + 默认图）
        return executor;
    }

    private static Agent fakeAgent(AgentType type, AgentResult result) {
        return new Agent() {
            @Override
            public AgentType type() { return type; }
            @Override
            public AgentResult execute(AgentContext ctx) { return result; }
        };
    }

    /** 可补偿 Agent：可控制补偿是否抛异常（验证 best-effort 不阻断链） */
    private static class FakeCompensableAgent implements Agent, Compensable {
        private final AgentType type;
        private final AgentResult result;
        private final AtomicBoolean compensated;
        private final boolean compensationThrows;

        FakeCompensableAgent(AgentType type, AgentResult result, AtomicBoolean compensated) {
            this(type, result, compensated, false);
        }

        FakeCompensableAgent(AgentType type, AgentResult result, AtomicBoolean compensated, boolean compensationThrows) {
            this.type = type;
            this.result = result;
            this.compensated = compensated;
            this.compensationThrows = compensationThrows;
        }

        @Override
        public AgentType type() { return type; }

        @Override
        public AgentResult execute(AgentContext ctx) { return result; }

        @Override
        public Compensation compensation(AgentContext ctx) {
            return new Compensation() {
                @Override
                public String description() { return "undo-" + type; }
                @Override
                public void execute(AgentContext ctx) {
                    compensated.set(true);
                    if (compensationThrows) {
                        throw new RuntimeException("补偿失败模拟");
                    }
                }
            };
        }
    }
}
