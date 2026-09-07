package com.knowledge.agent.engine;

/**
 * LLM 调用身份信息：在异步线程池场景（AgentExecutor/WorkflowExecutor）显式携带，
 * 不依赖 SecurityContextHolder（异步线程中 SecurityContext 不可用）。
 * <p>由 {@link AgentContext}/{@code WorkflowContext}/{@code ToolContext} 的 userId/tenantId 构造。
 *
 * @author: lxcechoo@gmail.com
 */
public record LlmIdentity(Long userId, String username, Long tenantId) {

    /** 简化构造：仅 userId + tenantId（username 缺省为 null，落库时记为空串） */
    public static LlmIdentity of(Long userId, Long tenantId) {
        return new LlmIdentity(userId, null, tenantId);
    }
}
