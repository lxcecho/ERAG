/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.custom.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.agent.custom.dto.MyTaskVo;
import com.knowledge.agent.custom.entity.AgentRun;
import com.knowledge.agent.custom.mapper.AgentRunMapper;
import com.knowledge.agent.engine.AgentTaskManager;
import com.knowledge.agent.entity.AgentTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link MyTaskService} 单元测试：agent_task 与 agent_run 合并、按创建时间倒序、agentName 填充、分页切片。
 */
@ExtendWith(MockitoExtension.class)
class MyTaskServiceTest {

    @Mock
    private AgentTaskManager taskManager;

    @Mock
    private AgentRunMapper runMapper;

    @Mock
    private AgentDefinitionService definitionService;

    @InjectMocks
    private MyTaskService service;

    @Test
    void should_merge_and_sort_by_create_time_desc() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 10, 0, 0);

        AgentTask task = new AgentTask();
        task.setId(1L);
        task.setGoal("分析销售数据");
        task.setStatus("RUNNING");
        task.setTokenUsage(100);
        task.setCreateTime(now);
        Page<AgentTask> taskPage = new Page<>(1, 10);
        taskPage.setRecords(List.of(task));
        taskPage.setTotal(1);
        when(taskManager.pageUserTasks(any(), eq(1L), eq(1L))).thenReturn(taskPage);

        AgentRun runNew = new AgentRun();
        runNew.setId(2L);
        runNew.setAgentId(50L);
        runNew.setQuestion("日志根因");
        runNew.setStatus("COMPLETED");
        runNew.setTokenUsage(50);
        runNew.setCreateTime(now.plusMinutes(5));
        AgentRun runOld = new AgentRun();
        runOld.setId(3L);
        runOld.setAgentId(60L);
        runOld.setQuestion("旧任务");
        runOld.setStatus("FAILED");
        runOld.setCreateTime(now.minusMinutes(5));
        Page<AgentRun> runPage = new Page<>(1, 10);
        runPage.setRecords(List.of(runNew, runOld));
        runPage.setTotal(2);
        when(runMapper.selectPage(any(), any())).thenReturn(runPage);

        when(definitionService.listNames(Set.of(50L, 60L)))
                .thenReturn(Map.of(50L, "日志分析助手", 60L, "报告助手"));

        IPage<MyTaskVo> result = service.page(1, 10, 1L, 1L);

        assertEquals(3, result.getTotal());
        List<MyTaskVo> records = result.getRecords();
        assertEquals(3, records.size());
        // 时间倒序：run(now+5m) → task(now) → run(now-5m)
        assertEquals("CUSTOM_AGENT", records.get(0).getTaskType());
        assertEquals("日志根因", records.get(0).getTitle());
        assertEquals("日志分析助手", records.get(0).getAgentName());
        assertEquals(50, records.get(0).getTokenUsage());
        assertEquals("AGENT", records.get(1).getTaskType());
        assertEquals("分析销售数据", records.get(1).getTitle());
        assertEquals("CUSTOM_AGENT", records.get(2).getTaskType());
        assertEquals("旧任务", records.get(2).getTitle());
        assertEquals("报告助手", records.get(2).getAgentName());
    }

    @Test
    void should_slice_page_for_current_page() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 10, 0, 0);

        AgentTask task = new AgentTask();
        task.setId(1L);
        task.setGoal("任务A");
        task.setStatus("RUNNING");
        task.setCreateTime(now);
        Page<AgentTask> taskPage = new Page<>(1, 1);
        taskPage.setRecords(List.of(task));
        taskPage.setTotal(5);
        when(taskManager.pageUserTasks(any(), eq(1L), eq(1L))).thenReturn(taskPage);

        AgentRun run = new AgentRun();
        run.setId(2L);
        run.setAgentId(50L);
        run.setQuestion("运行B");
        run.setStatus("COMPLETED");
        run.setCreateTime(now.plusMinutes(1));
        Page<AgentRun> runPage = new Page<>(1, 1);
        runPage.setRecords(List.of(run));
        runPage.setTotal(7);
        when(runMapper.selectPage(any(), any())).thenReturn(runPage);
        when(definitionService.listNames(any())).thenReturn(Map.of());

        // 合并列表 [run(now+1m), task(now)]，第 2 页第 1 条 → task
        IPage<MyTaskVo> result = service.page(2, 1, 1L, 1L);

        assertEquals(12, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("AGENT", result.getRecords().get(0).getTaskType());
        assertEquals("任务A", result.getRecords().get(0).getTitle());
        assertNull(result.getRecords().get(0).getAgentName());
    }
}
