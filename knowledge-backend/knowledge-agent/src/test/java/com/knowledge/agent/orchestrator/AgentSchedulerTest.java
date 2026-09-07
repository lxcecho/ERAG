/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.orchestrator;

import com.knowledge.agent.engine.AgentTaskManager;
import com.knowledge.agent.engine.AgentType;
import com.knowledge.agent.entity.AgentTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentScheduler} 单元测试：验证门面委托（注册/调度/创建任务）正确转发至执行器与任务管理器。
 */
@ExtendWith(MockitoExtension.class)
class AgentSchedulerTest {

    @Mock
    private AgentExecutor executor;

    @Mock
    private AgentTaskManager taskManager;

    @InjectMocks
    private AgentScheduler scheduler;

    @Test
    void should_delegate_create_task_to_task_manager() {
        AgentTask task = new AgentTask();
        task.setId(1L);
        when(taskManager.create(eq(1L), eq(10L), eq(100L), eq("goal"), eq(7L))).thenReturn(task);

        AgentTask result = scheduler.createTask(1L, 10L, 100L, "goal", 7L);

        assertSame(task, result);
        verify(taskManager).create(1L, 10L, 100L, "goal", 7L);
    }

    @Test
    void should_delegate_submit_to_executor_async() {
        doNothing().when(executor).executeAsync(eq(1001L), eq(1L));

        scheduler.submit(1001L, 1L);

        verify(executor).executeAsync(1001L, 1L);
    }

    @Test
    void should_delegate_sync_execute_to_executor() {
        AgentTask task = new AgentTask();
        task.setId(1001L);
        when(executor.execute(1001L)).thenReturn(task);

        AgentTask result = scheduler.execute(1001L);

        assertSame(task, result);
        verify(executor).execute(1001L);
    }

    @Test
    void should_throw_when_execute_unknown_graph_code() {
        when(executor.getGraph("missing")).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scheduler.execute(1001L, "missing"));
        assertTrue(ex.getMessage().contains("未注册的编排图"));
    }

    @Test
    void should_delegate_execute_named_graph() {
        AgentGraph graph = AgentGraph.builder()
                .code("custom")
                .node(AgentNode.builder().nodeId("planner").agentType(AgentType.PLANNER).name("p").build())
                .build();
        when(executor.getGraph("custom")).thenReturn(graph);
        AgentTask task = new AgentTask();
        task.setId(1001L);
        when(executor.execute(1001L, graph)).thenReturn(task);

        AgentTask result = scheduler.execute(1001L, "custom");

        assertSame(task, result);
        verify(executor).execute(1001L, graph);
    }

    @Test
    void should_delegate_cancel_to_executor() {
        doNothing().when(executor).cancel(eq(1001L), any());

        assertDoesNotThrow(() -> scheduler.cancel(1001L, "用户取消"));
        verify(executor).cancel(eq(1001L), eq("用户取消"));
    }

    @Test
    void should_delegate_register_and_default_graph() {
        AgentGraph graph = AgentGraph.builder()
                .code("custom")
                .node(AgentNode.builder().nodeId("planner").agentType(AgentType.PLANNER).name("p").build())
                .build();
        doNothing().when(executor).registerGraph(eq("custom"), any());
        when(executor.defaultGraph()).thenReturn(graph);
        when(executor.getGraph("custom")).thenReturn(graph);

        scheduler.registerGraph("custom", graph);
        assertNotNull(scheduler.defaultGraph());
        assertNotNull(scheduler.getGraph("custom"));

        verify(executor).registerGraph("custom", graph);
        verify(executor).defaultGraph();
    }
}
