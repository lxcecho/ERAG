package com.knowledge.agent.orchestrator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.agent.engine.AgentTaskManager;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.agent.mapper.AgentTaskMapper;
import com.knowledge.agent.orchestrator.entity.AgentCompensation;
import com.knowledge.agent.orchestrator.entity.AgentNodeRun;
import com.knowledge.agent.orchestrator.mapper.AgentCompensationMapper;
import com.knowledge.agent.orchestrator.mapper.AgentNodeRunMapper;
import com.knowledge.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 编排任务管理器：任务级（委托 {@link AgentTaskManager}）+ 节点级/补偿级持久化。
 * <p>
 * 镜像 {@code WorkflowTaskManager} 模式：所有写操作显式带 tenantId 兜底，
 * create_time 依赖数据库默认值，状态以枚举名存储。任务级状态写入 agent_task.status（VARCHAR），
 * 编排层用 {@link OrchestratorStatus} 字符串，与 engine 层 AgentStatus 共存不冲突（一任务仅经一层执行）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorTaskManager {

    private final AgentTaskManager delegate;
    private final AgentTaskMapper taskMapper;
    private final AgentNodeRunMapper nodeRunMapper;
    private final AgentCompensationMapper compensationMapper;

    // ==================== 任务级（委托 + 原始状态写入） ====================

    public AgentTask create(Long tenantId, Long userId, Long kbId, String goal, Long sessionId) {
        // AgentTaskManager.create 已将 status 置为 CREATED（与 OrchestratorStatus.CREATED 同名）
        return delegate.create(tenantId, userId, kbId, goal, sessionId);
    }

    public AgentTask getById(Long id) {
        return delegate.getById(id);
    }

    /** 写编排任务状态（直接写字符串到 agent_task.status，覆盖 ROLLING_BACK/CANCELED 等 engine 枚举外的值） */
    public void updateStatus(Long taskId, OrchestratorStatus status) {
        AgentTask t = new AgentTask();
        t.setId(taskId);
        t.setStatus(status.name());
        taskMapper.updateById(t);
    }

    public void completeTask(Long taskId, String result) {
        delegate.completeTask(taskId, result);
    }

    public void failTask(Long taskId, String errorCode, String errorMsg) {
        delegate.failTask(taskId, errorCode, errorMsg);
    }

    public void updateTokenUsage(Long taskId, int tokenUsage) {
        delegate.updateTokenUsage(taskId, tokenUsage);
    }

    public void recordArtifact(Long tenantId, Long taskId, Long stepId, String artifactType, Object payload) {
        delegate.recordArtifact(tenantId, taskId, stepId, artifactType, payload);
    }

    /** 取消任务（engine.AgentTaskManager 无 cancel，编排层自行写入 CANCELED） */
    public void cancelTask(Long taskId, String reason) {
        AgentTask t = new AgentTask();
        t.setId(taskId);
        t.setStatus(OrchestratorStatus.CANCELED.name());
        t.setErrorMsg(truncate(reason, 1024));
        t.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(t);
    }

    // ==================== 节点执行记录 ====================

    public AgentNodeRun startNodeRun(Long tenantId, Long taskId, String nodeId, String nodeName,
                                     String agentType, int runIndex, int attempt,
                                     long timeoutMs, String inputSummary) {
        AgentNodeRun run = new AgentNodeRun();
        run.setTenantId(tenantId);
        run.setTaskId(taskId);
        run.setNodeId(nodeId);
        run.setNodeName(nodeName);
        run.setAgentType(agentType);
        run.setRunIndex(runIndex);
        run.setAttempt(attempt);
        run.setStatus(NodeRunStatus.RUNNING.name());
        run.setTimeoutMs(timeoutMs);
        run.setInputSummary(truncate(inputSummary, 65535));
        run.setTokenUsage(0);
        run.setDurationMs(0L);
        run.setStartedAt(LocalDateTime.now());
        nodeRunMapper.insert(run);
        return run;
    }

    public void successNodeRun(Long runId, String outputSummary, int tokensUsed, long durationMs) {
        AgentNodeRun r = new AgentNodeRun();
        r.setId(runId);
        r.setStatus(NodeRunStatus.SUCCESS.name());
        r.setOutputSummary(truncate(outputSummary, 65535));
        r.setTokenUsage(tokensUsed);
        r.setDurationMs(durationMs);
        r.setFinishedAt(LocalDateTime.now());
        nodeRunMapper.updateById(r);
    }

    public void retryNodeRun(Long runId, String errorMsg) {
        AgentNodeRun r = new AgentNodeRun();
        r.setId(runId);
        r.setStatus(NodeRunStatus.RETRYING.name());
        r.setErrorMsg(truncate(errorMsg, 1024));
        r.setFinishedAt(LocalDateTime.now());
        nodeRunMapper.updateById(r);
    }

    public void timeoutNodeRun(Long runId, String errorMsg, long durationMs) {
        AgentNodeRun r = new AgentNodeRun();
        r.setId(runId);
        r.setStatus(NodeRunStatus.TIMEOUT.name());
        r.setErrorMsg(truncate(errorMsg, 1024));
        r.setDurationMs(durationMs);
        r.setFinishedAt(LocalDateTime.now());
        nodeRunMapper.updateById(r);
    }

    public void failNodeRun(Long runId, String errorMsg, long durationMs) {
        AgentNodeRun r = new AgentNodeRun();
        r.setId(runId);
        r.setStatus(NodeRunStatus.FAILED.name());
        r.setErrorMsg(truncate(errorMsg, 1024));
        r.setDurationMs(durationMs);
        r.setFinishedAt(LocalDateTime.now());
        nodeRunMapper.updateById(r);
    }

    /** 回滚跳过未执行的下游节点（审计完整） */
    public void skipNodeRun(Long tenantId, Long taskId, String nodeId, String nodeName,
                            String agentType, int runIndex) {
        AgentNodeRun run = new AgentNodeRun();
        run.setTenantId(tenantId);
        run.setTaskId(taskId);
        run.setNodeId(nodeId);
        run.setNodeName(nodeName);
        run.setAgentType(agentType);
        run.setRunIndex(runIndex);
        run.setAttempt(1);
        run.setStatus(NodeRunStatus.SKIPPED.name());
        nodeRunMapper.insert(run);
    }

    public List<AgentNodeRun> listNodeRuns(Long taskId) {
        return nodeRunMapper.selectList(new LambdaQueryWrapper<AgentNodeRun>()
                .eq(AgentNodeRun::getTaskId, taskId)
                .orderByAsc(AgentNodeRun::getRunIndex)
                .orderByAsc(AgentNodeRun::getAttempt));
    }

    // ==================== 补偿记录 ====================

    public AgentCompensation startCompensation(Long tenantId, Long taskId, String nodeId,
                                               Long runId, String description) {
        AgentCompensation c = new AgentCompensation();
        c.setTenantId(tenantId);
        c.setTaskId(taskId);
        c.setNodeId(nodeId);
        c.setRunId(runId);
        c.setDescription(truncate(description, 512));
        c.setStatus("RUNNING");
        c.setDurationMs(0L);
        c.setStartedAt(LocalDateTime.now());
        compensationMapper.insert(c);
        return c;
    }

    public void successCompensation(Long id, long durationMs) {
        AgentCompensation c = new AgentCompensation();
        c.setId(id);
        c.setStatus("SUCCESS");
        c.setDurationMs(durationMs);
        c.setFinishedAt(LocalDateTime.now());
        compensationMapper.updateById(c);
    }

    public void failCompensation(Long id, String errorMsg, long durationMs) {
        AgentCompensation c = new AgentCompensation();
        c.setId(id);
        c.setStatus("FAILED");
        c.setErrorMsg(truncate(errorMsg, 1024));
        c.setDurationMs(durationMs);
        c.setFinishedAt(LocalDateTime.now());
        compensationMapper.updateById(c);
    }

    public List<AgentCompensation> listCompensations(Long taskId) {
        return compensationMapper.selectList(new LambdaQueryWrapper<AgentCompensation>()
                .eq(AgentCompensation::getTaskId, taskId)
                .orderByDesc(AgentCompensation::getId));
    }

    // ==================== 工具 ====================

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }
}
