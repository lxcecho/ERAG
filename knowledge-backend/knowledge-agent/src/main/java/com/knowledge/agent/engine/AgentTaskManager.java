package com.knowledge.agent.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.entity.AgentArtifact;
import com.knowledge.agent.entity.AgentMessage;
import com.knowledge.agent.entity.AgentStep;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.agent.mapper.AgentArtifactMapper;
import com.knowledge.agent.mapper.AgentMessageMapper;
import com.knowledge.agent.mapper.AgentStepMapper;
import com.knowledge.agent.mapper.AgentTaskMapper;
import com.knowledge.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 任务管理器：负责任务/步骤/消息/产物的持久化与生命周期管理。
 * <p>
 * 所有写操作显式带 tenantId（即使 {@code TenantContext} 未启用也能正确隔离）；
 * create_time/update_time 依赖数据库默认值（DEFAULT CURRENT_TIMESTAMP）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskManager {

    private final AgentTaskMapper taskMapper;
    private final AgentStepMapper stepMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentArtifactMapper artifactMapper;
    private final ObjectMapper objectMapper;

    // ==================== 任务 ====================

    public AgentTask create(Long tenantId, Long userId, Long kbId, String goal, Long sessionId) {
        AgentTask task = new AgentTask();
        task.setTenantId(tenantId);
        task.setUserId(userId);
        task.setKbId(kbId);
        task.setSessionId(sessionId);
        task.setGoal(goal);
        task.setStatus(AgentStatus.CREATED.name());
        task.setStepCount(0);
        task.setToolCallCount(0);
        task.setTokenUsage(0);
        taskMapper.insert(task);
        return task;
    }

    public AgentTask getById(Long id) {
        AgentTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException("Agent 任务不存在: " + id);
        }
        return task;
    }

    public void startTask(Long id) {
        updateStatus(id, AgentStatus.EXECUTING);
    }

    /** 分页查询某用户的任务（列表用，不含步骤/产物） */
    public IPage<AgentTask> pageUserTasks(Page<AgentTask> page, Long tenantId, Long userId) {
        return taskMapper.selectPage(page, new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTenantId, tenantId)
                .eq(AgentTask::getUserId, userId)
                .orderByDesc(AgentTask::getCreateTime));
    }

    public void updateStatus(Long id, AgentStatus status) {
        AgentTask task = new AgentTask();
        task.setId(id);
        task.setStatus(status.name());
        taskMapper.updateById(task);
    }

    /** 任务完成：写入最终报告 + 终态 */
    public void completeTask(Long taskId, String result) {
        AgentTask task = new AgentTask();
        task.setId(taskId);
        task.setStatus(AgentStatus.COMPLETED.name());
        task.setResult(result);
        task.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /** 任务失败：写入错误信息 + 终态 */
    public void failTask(Long taskId, String errorCode, String errorMsg) {
        AgentTask task = new AgentTask();
        task.setId(taskId);
        task.setStatus(AgentStatus.FAILED.name());
        task.setErrorCode(errorCode);
        task.setErrorMsg(truncate(errorMsg, 1024));
        task.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /** 回写任务累计 token 消耗（工作流收尾或预算校验时调用） */
    public void updateTokenUsage(Long taskId, int tokenUsage) {
        AgentTask task = new AgentTask();
        task.setId(taskId);
        task.setTokenUsage(tokenUsage);
        taskMapper.updateById(task);
    }

    // ==================== 步骤 ====================

    public AgentStep startStep(Long tenantId, Long taskId, int stepIndex, AgentType type, String inputSummary) {
        AgentStep step = new AgentStep();
        step.setTenantId(tenantId);
        step.setTaskId(taskId);
        step.setStepIndex(stepIndex);
        step.setAgentType(type.name());
        step.setStatus(StepStatus.RUNNING.name());
        step.setInputSummary(truncate(inputSummary, 65535));
        step.setTokenUsage(0);
        step.setDurationMs(0L);
        LocalDateTime now = LocalDateTime.now();
        step.setStartedAt(now);
        stepMapper.insert(step);

        // 步骤计数 +1
        AgentTask task = new AgentTask();
        task.setId(taskId);
        // 乐观累加：通过 SQL 表达式不可移植，这里先查再写（任务级并发低，可接受）
        AgentTask current = taskMapper.selectById(taskId);
        if (current != null) {
            task.setStepCount((current.getStepCount() == null ? 0 : current.getStepCount()) + 1);
            taskMapper.updateById(task);
        }
        return step;
    }

    public void successStep(Long stepId, String outputSummary, int tokensUsed, long durationMs) {
        AgentStep step = new AgentStep();
        step.setId(stepId);
        step.setStatus(StepStatus.SUCCESS.name());
        step.setOutputSummary(truncate(outputSummary, 65535));
        step.setTokenUsage(tokensUsed);
        step.setDurationMs(durationMs);
        step.setFinishedAt(LocalDateTime.now());
        stepMapper.updateById(step);
    }

    public void failStep(Long stepId, String errorMsg) {
        AgentStep step = new AgentStep();
        step.setId(stepId);
        step.setStatus(StepStatus.FAILED.name());
        step.setErrorMsg(truncate(errorMsg, 1024));
        step.setFinishedAt(LocalDateTime.now());
        stepMapper.updateById(step);
    }

    public List<AgentStep> listSteps(Long taskId) {
        return stepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getTaskId, taskId)
                .orderByAsc(AgentStep::getStepIndex));
    }

    // ==================== 产物 ====================

    public void recordArtifact(Long tenantId, Long taskId, Long stepId, String artifactType, Object payload) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setTenantId(tenantId);
        artifact.setTaskId(taskId);
        artifact.setStepId(stepId);
        artifact.setArtifactType(artifactType);
        artifact.setPayload(serialize(payload));
        artifactMapper.insert(artifact);
    }

    public List<AgentArtifact> listArtifacts(Long taskId) {
        return artifactMapper.selectList(new LambdaQueryWrapper<AgentArtifact>()
                .eq(AgentArtifact::getTaskId, taskId)
                .orderByAsc(AgentArtifact::getId));
    }

    // ==================== 消息 ====================

    public void recordMessage(Long tenantId, Long taskId, Long stepId, String role, String content, String toolName) {
        AgentMessage msg = new AgentMessage();
        msg.setTenantId(tenantId);
        msg.setTaskId(taskId);
        msg.setStepId(stepId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolName(toolName);
        msg.setTokenUsage(0);
        messageMapper.insert(msg);
    }

    // ==================== 工具方法 ====================

    private String serialize(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("产物序列化失败，降级为 toString: {}", e.getMessage());
            return payload.toString();
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }
}
