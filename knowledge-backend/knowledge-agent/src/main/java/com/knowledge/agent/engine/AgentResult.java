package com.knowledge.agent.engine;

import lombok.Getter;

/**
 * Agent 执行结果。
 * <p>成功时携带产物 key + 结构化 payload（写入 Context 供下游 Agent 引用）；
 * 失败时携带错误信息（触发任务终止）。
 * <p>可选携带 {@code suggestedNextNodeId}：Agent 建议的下一个节点 ID，
 * 执行器校验合法性后决定跳转；为空时走默认顺序。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
public class AgentResult {

    private final boolean success;
    /** 产物 key（PLAN/EVIDENCES/ANALYSIS/REPORT），成功时非空则写入 Context 与 artifact 表 */
    private final String artifactKey;
    /** 产物 payload（Plan / List&lt;Evidence&gt; / String） */
    private final Object artifact;
    /** 人类可读摘要（写入 step.output_summary，便于审计） */
    private final String summary;
    /** 失败原因 */
    private final String errorMessage;
    /** 本次 LLM 调用 token 消耗（0 表示未调用 LLM，如 KnowledgeAgent） */
    private final int tokensUsed;
    /** Agent 建议的下一个节点 ID（为空时走默认顺序；执行器校验合法性后跳转） */
    private final String suggestedNextNodeId;

    private AgentResult(boolean success, String artifactKey, Object artifact,
                        String summary, String errorMessage, int tokensUsed,
                        String suggestedNextNodeId) {
        this.success = success;
        this.artifactKey = artifactKey;
        this.artifact = artifact;
        this.summary = summary;
        this.errorMessage = errorMessage;
        this.tokensUsed = tokensUsed;
        this.suggestedNextNodeId = suggestedNextNodeId;
    }

    /** 成功：携带产物 + 摘要 */
    public static AgentResult success(String artifactKey, Object artifact, String summary) {
        return new AgentResult(true, artifactKey, artifact, summary, null, 0, null);
    }

    /** 成功：携带产物 + 摘要 + 本次 LLM 调用 token 消耗（供 Executor 累加做 token 预算校验） */
    public static AgentResult success(String artifactKey, Object artifact, String summary, int tokensUsed) {
        return new AgentResult(true, artifactKey, artifact, summary, null, tokensUsed, null);
    }

    /** 成功：携带产物，摘要默认空 */
    public static AgentResult success(String artifactKey, Object artifact) {
        return success(artifactKey, artifact, null);
    }

    /** 成功：携带产物 + 摘要 + Agent 建议的下一个节点 ID */
    public static AgentResult success(String artifactKey, Object artifact, String summary,
                                       int tokensUsed, String suggestedNextNodeId) {
        return new AgentResult(true, artifactKey, artifact, summary, null, tokensUsed, suggestedNextNodeId);
    }

    /** 失败：携带错误信息 */
    public static AgentResult failure(String errorMessage) {
        return new AgentResult(false, null, null, null, errorMessage, 0, null);
    }
}
