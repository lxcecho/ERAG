/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.agent.dto.AgentStartRequest;
import com.knowledge.agent.dto.AgentTaskVo;
import com.knowledge.agent.engine.AgentExecutor;
import com.knowledge.agent.engine.AgentStatus;
import com.knowledge.agent.engine.AgentTaskManager;
import com.knowledge.agent.entity.AgentArtifact;
import com.knowledge.agent.entity.AgentStep;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.result.Result;
import com.knowledge.kb.service.KbPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentController} 单元测试：验证启动/详情/分页接口与权限门禁。
 * <p>直接实例化 Controller（非 @WebMvcTest），避免 Spring Security 过滤器链干扰；
 * {@code SecurityUtils.currentUserId()} 为静态方法，使用 {@code MockedStatic} 桩；
 * {@code TenantContext} 为 ThreadLocal，直接 set/clear 即可。
 */
@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentTaskManager taskManager;
    @Mock
    private AgentExecutor executor;
    @Mock
    private KbPermissionService kbPermissionService;

    private AgentController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentController(taskManager, executor, kbPermissionService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 启动任务 ====================

    @Test
    void should_start_task_and_return_id_when_has_permission() {
        try (var mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUserId).thenReturn(10L);
            TenantContext.setTenantId(1L);

            AgentTask task = newTask(2001L, AgentStatus.CREATED);
            when(taskManager.create(1L, 10L, 100L, "分析销售政策", null)).thenReturn(task);

            AgentStartRequest req = new AgentStartRequest();
            req.setKbId(100L);
            req.setGoal("分析销售政策");

            Result<Long> result = controller.start(req);

            assertTrue(result.isSuccess());
            assertEquals(2001L, result.getData());
            verify(kbPermissionService).checkViewer(100L, 10L);
            verify(executor).executeAsync(2001L, 1L);
        }
    }

    @Test
    void should_throw_403_when_no_kb_permission() {
        try (var mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUserId).thenReturn(10L);
            TenantContext.setTenantId(1L);

            doThrow(new BizException(403, "无访问该知识库的权限"))
                    .when(kbPermissionService).checkViewer(100L, 10L);

            AgentStartRequest req = new AgentStartRequest();
            req.setKbId(100L);
            req.setGoal("分析销售政策");

            BizException ex = assertThrows(BizException.class, () -> controller.start(req));
            assertEquals(403, ex.getCode());
            // 权限不通过 → 不应创建任务、不应启动执行
            verify(taskManager, never()).create(anyLong(), anyLong(), anyLong(), any(), any());
            verify(executor, never()).executeAsync(anyLong(), anyLong());
        }
    }

    // ==================== 任务详情 ====================

    @Test
    void should_return_detail_with_steps_and_artifacts() {
        AgentTask task = newTask(2001L, AgentStatus.COMPLETED);
        task.setResult("# 分析报告");
        task.setStepCount(4);
        task.setTokenUsage(8200);
        when(taskManager.getById(2001L)).thenReturn(task);
        when(taskManager.listSteps(2001L)).thenReturn(List.of(buildStep(1, "PLANNER")));
        when(taskManager.listArtifacts(2001L)).thenReturn(List.of(buildArtifact("REPORT", "# 报告")));

        Result<AgentTaskVo> result = controller.detail(2001L);

        assertTrue(result.isSuccess());
        AgentTaskVo vo = result.getData();
        assertEquals(2001L, vo.getId());
        assertEquals("COMPLETED", vo.getStatus());
        assertEquals("# 分析报告", vo.getResult());
        assertEquals(1, vo.getSteps().size());
        assertEquals("PLANNER", vo.getSteps().get(0).getAgentType());
        assertEquals(1, vo.getArtifacts().size());
        assertEquals("REPORT", vo.getArtifacts().get(0).getArtifactType());
    }

    @Test
    void should_return_empty_lists_when_task_has_no_steps_or_artifacts() {
        AgentTask task = newTask(2002L, AgentStatus.EXECUTING);
        when(taskManager.getById(2002L)).thenReturn(task);
        when(taskManager.listSteps(2002L)).thenReturn(List.of());
        when(taskManager.listArtifacts(2002L)).thenReturn(List.of());

        Result<AgentTaskVo> result = controller.detail(2002L);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().getSteps().isEmpty());
        assertTrue(result.getData().getArtifacts().isEmpty());
    }

    // ==================== 分页列表 ====================

    @Test
    void should_return_paginated_tasks_for_current_user() {
        try (var mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUserId).thenReturn(10L);
            TenantContext.setTenantId(1L);

            AgentTask task = newTask(2001L, AgentStatus.COMPLETED);
            Page<AgentTask> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(task));
            mockPage.setTotal(1);
            when(taskManager.pageUserTasks(any(Page.class), anyLong(), anyLong())).thenReturn(mockPage);

            Result<IPage<AgentTaskVo>> result = controller.page(1, 10);

            assertTrue(result.isSuccess());
            assertEquals(1, result.getData().getTotal());
            assertEquals(1, result.getData().getRecords().size());
            // 列表用轻量 VO，不应查询步骤/产物
            AgentTaskVo vo = result.getData().getRecords().get(0);
            assertEquals(2001L, vo.getId());
            assertTrue(vo.getSteps() == null || vo.getSteps().isEmpty(),
                    "列表 VO 不应携带步骤数据");
        }
    }

    // ==================== 测试辅助 ====================

    private static AgentTask newTask(Long id, AgentStatus status) {
        AgentTask task = new AgentTask();
        task.setId(id);
        task.setTenantId(1L);
        task.setUserId(10L);
        task.setKbId(100L);
        task.setGoal("分析销售政策");
        task.setStatus(status.name());
        return task;
    }

    private static AgentStep buildStep(int index, String agentType) {
        AgentStep step = new AgentStep();
        step.setStepIndex(index);
        step.setAgentType(agentType);
        step.setStatus("SUCCESS");
        step.setOutputSummary("完成");
        step.setDurationMs(1000L);
        return step;
    }

    private static AgentArtifact buildArtifact(String type, String payload) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setArtifactType(type);
        artifact.setPayload(payload);
        return artifact;
    }
}
