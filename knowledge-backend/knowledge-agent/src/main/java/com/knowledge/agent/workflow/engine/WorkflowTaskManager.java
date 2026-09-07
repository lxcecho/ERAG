package com.knowledge.agent.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.agent.workflow.entity.WorkflowNodeRun;
import com.knowledge.agent.workflow.entity.WorkflowTask;
import com.knowledge.agent.workflow.enums.NodeRunStatus;
import com.knowledge.agent.workflow.enums.WorkflowStatus;
import com.knowledge.agent.workflow.mapper.WorkflowNodeRunMapper;
import com.knowledge.agent.workflow.mapper.WorkflowTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程任务管理器：任务与节点执行记录的持久化与生命周期管理。
 * <p>镜像 {@code AgentTaskManager} 模式：所有写操作显式带 tenantId 兜底，
 * create_time/update_time 依赖数据库默认值，状态以枚举名存储。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTaskManager {

    private final WorkflowTaskMapper taskMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final ObjectMapper objectMapper;

    // ==================== 任务 ====================

    public WorkflowTask create(Long tenantId, Long userId, Long definitionId,
                               Long kbId, String goal, String businessKey) {
        WorkflowTask task = new WorkflowTask();
        task.setTenantId(tenantId);
        task.setUserId(userId);
        task.setDefinitionId(definitionId);
        task.setKbId(kbId);
        task.setBusinessKey(businessKey);
        task.setGoal(goal);
        task.setStatus(WorkflowStatus.CREATED.name());
        task.setNodeCount(0);
        task.setRetryCount(0);
        task.setTokenUsage(0);
        taskMapper.insert(task);
        return task;
    }

    public WorkflowTask getById(Long id) {
        WorkflowTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException("流程任务不存在: " + id);
        }
        return task;
    }

    public void updateStatus(Long id, WorkflowStatus status) {
        WorkflowTask t = new WorkflowTask();
        t.setId(id);
        t.setStatus(status.name());
        taskMapper.updateById(t);
    }

    /** 暂停等待人工：记录断点节点 */
    public void pauseForHuman(Long id, String currentNode) {
        WorkflowTask t = new WorkflowTask();
        t.setId(id);
        t.setStatus(WorkflowStatus.WAITING_HUMAN.name());
        t.setCurrentNode(currentNode);
        taskMapper.updateById(t);
    }

    /** 状态保存：持久化上下文变量 + 断点 + 计数（每个节点执行后调用，支持恢复） */
    public void saveProgress(Long id, String contextJson, String currentNode,
                             int nodeCount, int tokenUsage, int retryCount) {
        WorkflowTask t = new WorkflowTask();
        t.setId(id);
        t.setContextJson(contextJson);
        t.setCurrentNode(currentNode);
        t.setNodeCount(nodeCount);
        t.setTokenUsage(tokenUsage);
        t.setRetryCount(retryCount);
        taskMapper.updateById(t);
    }

    public void completeTask(Long id, String result) {
        WorkflowTask t = new WorkflowTask();
        t.setId(id);
        t.setStatus(WorkflowStatus.COMPLETED.name());
        t.setResult(result);
        t.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(t);
    }

    public void failTask(Long id, String errorCode, String errorMsg) {
        WorkflowTask t = new WorkflowTask();
        t.setId(id);
        t.setStatus(WorkflowStatus.FAILED.name());
        t.setErrorCode(errorCode);
        t.setErrorMsg(truncate(errorMsg, 1024));
        t.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(t);
    }

    public void cancelTask(Long id, String reason) {
        WorkflowTask t = new WorkflowTask();
        t.setId(id);
        t.setStatus(WorkflowStatus.CANCELED.name());
        t.setErrorMsg(truncate(reason, 1024));
        t.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(t);
    }

    public IPage<WorkflowTask> pageUserTasks(Page<WorkflowTask> page, Long tenantId, Long userId, String status) {
        LambdaQueryWrapper<WorkflowTask> wrapper = new LambdaQueryWrapper<WorkflowTask>()
                .eq(WorkflowTask::getTenantId, tenantId)
                .eq(WorkflowTask::getUserId, userId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(WorkflowTask::getStatus, status);
        }
        wrapper.orderByDesc(WorkflowTask::getCreateTime);
        return taskMapper.selectPage(page, wrapper);
    }

    // ==================== 节点执行记录 ====================

    public WorkflowNodeRun startNodeRun(Long tenantId, Long taskId, String nodeId, String nodeName,
                                        String nodeType, int runIndex, int attempt, String inputJson) {
        WorkflowNodeRun run = new WorkflowNodeRun();
        run.setTenantId(tenantId);
        run.setTaskId(taskId);
        run.setNodeId(nodeId);
        run.setNodeName(nodeName);
        run.setNodeType(nodeType);
        run.setRunIndex(runIndex);
        run.setAttempt(attempt);
        run.setStatus(NodeRunStatus.RUNNING.name());
        run.setInputJson(inputJson);
        run.setTokenUsage(0);
        run.setDurationMs(0L);
        run.setStartedAt(LocalDateTime.now());
        nodeRunMapper.insert(run);
        return run;
    }

    public void successNodeRun(Long runId, String outputJson, int tokensUsed, long durationMs) {
        WorkflowNodeRun r = new WorkflowNodeRun();
        r.setId(runId);
        r.setStatus(NodeRunStatus.SUCCESS.name());
        r.setOutputJson(outputJson);
        r.setTokenUsage(tokensUsed);
        r.setDurationMs(durationMs);
        r.setFinishedAt(LocalDateTime.now());
        nodeRunMapper.updateById(r);
    }

    public void failNodeRun(Long runId, String errorMsg) {
        WorkflowNodeRun r = new WorkflowNodeRun();
        r.setId(runId);
        r.setStatus(NodeRunStatus.FAILED.name());
        r.setErrorMsg(truncate(errorMsg, 1024));
        r.setFinishedAt(LocalDateTime.now());
        nodeRunMapper.updateById(r);
    }

    /** HUMAN 节点暂停：标记等待审批 */
    public void waitHumanNodeRun(Long runId) {
        WorkflowNodeRun r = new WorkflowNodeRun();
        r.setId(runId);
        r.setStatus(NodeRunStatus.WAITING_HUMAN.name());
        r.setFinishedAt(LocalDateTime.now());
        nodeRunMapper.updateById(r);
    }

    /** 节点跳过（HUMAN 驳回后的下游节点） */
    public void skipNodeRun(Long tenantId, Long taskId, String nodeId, String nodeName,
                            String nodeType, int runIndex) {
        WorkflowNodeRun run = new WorkflowNodeRun();
        run.setTenantId(tenantId);
        run.setTaskId(taskId);
        run.setNodeId(nodeId);
        run.setNodeName(nodeName);
        run.setNodeType(nodeType);
        run.setRunIndex(runIndex);
        run.setAttempt(1);
        run.setStatus(NodeRunStatus.SKIPPED.name());
        nodeRunMapper.insert(run);
    }

    /** 审批结果回写 HUMAN 节点记录 */
    public void approveNodeRun(Long runId, boolean approved, Long approverUserId, String comment) {
        WorkflowNodeRun r = new WorkflowNodeRun();
        r.setId(runId);
        r.setStatus(NodeRunStatus.SUCCESS.name());
        r.setApproved(approved ? 1 : 0);
        r.setApproverUserId(approverUserId);
        r.setApprovalComment(truncate(comment, 1024));
        nodeRunMapper.updateById(r);
    }

    /** 取任务某节点最新的 WAITING_HUMAN 记录（恢复执行用） */
    public WorkflowNodeRun findWaitingHumanRun(Long taskId, String nodeId) {
        return nodeRunMapper.selectOne(new LambdaQueryWrapper<WorkflowNodeRun>()
                .eq(WorkflowNodeRun::getTaskId, taskId)
                .eq(WorkflowNodeRun::getNodeId, nodeId)
                .eq(WorkflowNodeRun::getStatus, NodeRunStatus.WAITING_HUMAN.name())
                .orderByDesc(WorkflowNodeRun::getAttempt)
                .last("LIMIT 1"));
    }

    public List<WorkflowNodeRun> listNodeRuns(Long taskId) {
        return nodeRunMapper.selectList(new LambdaQueryWrapper<WorkflowNodeRun>()
                .eq(WorkflowNodeRun::getTaskId, taskId)
                .orderByAsc(WorkflowNodeRun::getRunIndex)
                .orderByAsc(WorkflowNodeRun::getAttempt));
    }

    // ==================== 工具 ====================

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }
}
