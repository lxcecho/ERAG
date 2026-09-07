package com.knowledge.ai.model;

import java.math.BigDecimal;

/**
 * 模型路由上下文：携带本次调用影响路由决策的全部维度。
 * <p>不可变 record，由调用方构造；{@link com.knowledge.ai.model.impl.ModelRouterImpl} 在入口处
 * 补全 tenantId（缺省时取 {@code TenantContext}），再交给 {@link ModelStrategy} 决策。
 *
 * @param tenantId       租户 ID（按租户路由用，空则由 Router 从 TenantContext 补全）
 * @param strategy       路由策略提示（空则用默认策略）
 * @param preferredModel 偏好模型名（按名路由用）
 * @param tokenBudget    token 预算上限（按 token 路由用，预估超限则跳过该供应商）
 * @param costBudget     单次费用预算上限（按成本路由用，超限则跳过）
 * @param scenario       业务场景标识（如 rag_chat / agent_plan，供扩展场景化路由）
 *
 * @author: lxcechoo@gmail.com
 */
public record ModelContext(
        Long tenantId,
        RouteStrategyType strategy,
        String preferredModel,
        Integer tokenBudget,
        BigDecimal costBudget,
        String scenario
) {

    /** 空上下文（全部走默认） */
    public static ModelContext empty() {
        return new ModelContext(null, null, null, null, null, null);
    }

    /** 指定租户 + 策略 */
    public static ModelContext of(Long tenantId, RouteStrategyType strategy) {
        return new ModelContext(tenantId, strategy, null, null, null, null);
    }
}
