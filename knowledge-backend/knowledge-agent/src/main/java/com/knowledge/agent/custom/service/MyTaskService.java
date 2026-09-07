package com.knowledge.agent.custom.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.agent.custom.dto.MyTaskVo;
import com.knowledge.agent.custom.entity.AgentRun;
import com.knowledge.agent.custom.mapper.AgentRunMapper;
import com.knowledge.agent.engine.AgentTaskManager;
import com.knowledge.agent.entity.AgentTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 我的任务聚合服务：合并 自主式 Agent 任务（agent_task）与 自定义 Agent 运行记录（agent_run），
 * 按创建时间倒序统一分页（两个数据源各取最新 pageSize 条后合并排序再取本页，demo 级精确分页）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyTaskService {

    private final AgentTaskManager taskManager;
    private final AgentRunMapper runMapper;
    private final AgentDefinitionService definitionService;

    /**
     * 聚合分页查询。
     *
     * @param current  页码（从1开始）
     * @param size     每页条数
     * @param tenantId 租户ID
     * @param userId   当前用户ID
     * @return 合并后按 create_time 倒序的分页结果
     */
    public IPage<MyTaskVo> page(long current, long size, Long tenantId, Long userId) {
        // 两个数据源各取最新 size 条，合并排序后截取本页（字符串列名便于单测，规避 lambda cache 未初始化）
        IPage<AgentTask> taskPage = taskManager.pageUserTasks(new Page<>(1, size), tenantId, userId);
        QueryWrapper<AgentRun> wrapper = new QueryWrapper<AgentRun>()
                .eq("tenant_id", tenantId)
                .eq("user_id", userId)
                .orderByDesc("create_time");
        IPage<AgentRun> runPage = runMapper.selectPage(new Page<>(1, size), wrapper);

        List<MyTaskVo> items = new ArrayList<>(taskPage.getRecords().size() + runPage.getRecords().size());
        // Agent 任务
        for (AgentTask t : taskPage.getRecords()) {
            MyTaskVo vo = new MyTaskVo();
            vo.setTaskType("AGENT");
            vo.setTaskId(t.getId());
            vo.setTitle(t.getGoal());
            vo.setStatus(t.getStatus());
            vo.setTokenUsage(t.getTokenUsage());
            vo.setErrorMsg(t.getErrorMsg());
            vo.setCreateTime(t.getCreateTime());
            vo.setFinishedTime(t.getFinishedTime());
            items.add(vo);
        }
        // 自定义 Agent 运行记录（批量填充 agentName 避免 N+1）
        Set<Long> agentIds = runPage.getRecords().stream().map(AgentRun::getAgentId).collect(Collectors.toSet());
        Map<Long, String> names = agentIds.isEmpty() ? Map.of() : definitionService.listNames(agentIds);
        for (AgentRun r : runPage.getRecords()) {
            MyTaskVo vo = new MyTaskVo();
            vo.setTaskType("CUSTOM_AGENT");
            vo.setTaskId(r.getId());
            vo.setAgentId(r.getAgentId());
            vo.setAgentName(names.get(r.getAgentId()));
            vo.setTitle(r.getQuestion());
            vo.setStatus(r.getStatus());
            vo.setTokenUsage(r.getTokenUsage());
            vo.setErrorMsg(r.getErrorMsg());
            vo.setCreateTime(r.getCreateTime());
            vo.setFinishedTime(r.getFinishedTime());
            items.add(vo);
        }
        // 按创建时间倒序（空值排最后）
        items.sort(Comparator.comparing(MyTaskVo::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        long total = taskPage.getTotal() + runPage.getTotal();
        int from = (int) Math.min((current - 1) * size, items.size());
        int to = (int) Math.min(from + size, items.size());
        Page<MyTaskVo> result = new Page<>(current, size, total);
        result.setRecords(items.subList(from, to));
        log.info("[我的任务] tenant={} user={} total={} page={}/{}", tenantId, userId, total, current, size);
        return result;
    }
}
