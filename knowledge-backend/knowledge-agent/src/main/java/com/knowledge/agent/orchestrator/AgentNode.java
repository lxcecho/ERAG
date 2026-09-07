package com.knowledge.agent.orchestrator;

import com.knowledge.agent.engine.AgentType;
import lombok.Builder;
import lombok.Data;

/**
 * 编排图节点：包装一个 {@link AgentType}（运行时由 AgentScheduler 解析为具体 Agent）+ 路由与执行策略。
 * <p>
 * 图模型为"顺序 + 分支"：节点通过 {@link #next} 显式指向后继节点ID（null=列表顺序后继），
 * 覆盖企业主流的顺序 + 条件跳转形态，避免完整 DAG 并行调度的线程安全复杂度。
 * <p>
 * 执行策略：
 * <ul>
 *   <li>{@link #maxRetries}：额外重试次数（不含首次），0=失败即终止；</li>
 *   <li>{@link #retryBackoffMs}：重试退避（毫秒，按 attempt 线性递增）；</li>
 *   <li>{@link #timeoutMs}：单节点硬超时（0=不限，由任务级时长预算兜底）；</li>
 *   <li>{@link #outputArtifactKey}：产物写入 AgentContext 的 key（默认=agentType.name()）。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Builder
public class AgentNode {

    /** 节点ID（图内唯一，如 "planner" / "knowledge"） */
    private String nodeId;

    /** Agent 角色类型（运行时解析为 Agent 实例） */
    private AgentType agentType;

    /** 节点名称（中文，审计/展示用） */
    private String name;

    /** 下一节点ID（null=列表顺序后继；末节点 null=END）；显式指向可实现分支跳转 */
    private String next;

    /** 失败重试上限（不含首次；0=不重试） */
    @Builder.Default
    private int maxRetries = 0;

    /** 重试退避（毫秒，实际等待 = backoff * attempt） */
    @Builder.Default
    private long retryBackoffMs = 0L;

    /** 单节点硬超时（毫秒，0=不限） */
    @Builder.Default
    private long timeoutMs = 0L;

    /** 产物写入 AgentContext 的 key（null=使用 agentType.name()） */
    private String outputArtifactKey;

    /** 解析产物 key：显式优先，默认 agentType 名 */
    public String resolveArtifactKey() {
        return outputArtifactKey != null && !outputArtifactKey.isBlank()
                ? outputArtifactKey : agentType.name();
    }
}
