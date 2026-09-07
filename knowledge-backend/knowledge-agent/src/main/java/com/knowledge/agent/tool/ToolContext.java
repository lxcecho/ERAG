package com.knowledge.agent.tool;

import com.knowledge.agent.entity.AgentTask;
import lombok.Builder;
import lombok.Getter;

/**
 * 工具执行上下文：携带调用方的身份信息，贯穿权限校验 → 执行 → 审计记录。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>身份显式传递：tenantId / userId / kbId 由调用方（Agent 或 Controller）显式传入，
 *       不依赖 ThreadLocal 隐式获取，避免线程池复用导致的串租户问题；</li>
 *   <li>审计关联：taskId / stepId 将工具调用关联到具体任务与步骤，便于回放与审计；</li>
 *   <li>ToolExecutor 在执行前会将 tenantId 写入 {@code TenantContext}（TransmittableThreadLocal），
 *       保证工具内部依赖 MyBatis-Plus 租户拦截器的 Service 调用正确隔离。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
@Builder
public class ToolContext {

    /** 租户ID（多租户隔离，必填） */
    private final Long tenantId;

    /** 当前用户ID（权限判定主体，必填） */
    private final Long userId;

    /** 知识库ID（检索范围 / 权限校验范围，authRequired=true 时必填） */
    private final Long kbId;

    /** 关联 Agent 任务ID（审计用，可空——直接调用工具时为 null） */
    private final Long taskId;

    /** 关联 Agent 步骤ID（审计用，可空） */
    private final Long stepId;

    /**
     * 从 AgentTask 构建上下文（Agent 工作流内调用工具的典型场景）。
     *
     * @param task  Agent 任务
     * @param stepId 步骤ID（可空）
     * @return 工具上下文
     */
    public static ToolContext of(AgentTask task, Long stepId) {
        return ToolContext.builder()
                .tenantId(task.getTenantId())
                .userId(task.getUserId())
                .kbId(task.getKbId())
                .taskId(task.getId())
                .stepId(stepId)
                .build();
    }
}
