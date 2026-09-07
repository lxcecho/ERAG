package com.knowledge.ai.model;

import java.util.List;

/**
 * 模型路由策略：从候选供应商中按特定维度选出最优者。
 * <p>每个策略实现一种路由维度（按名 / 按成本 / 按 token / 按租户），
 * 由 {@link com.knowledge.ai.model.impl.ModelRouterImpl} 根据上下文策略类型分发调用。
 * <p>返回 {@code null} 表示该策略无法决策，Router 将回退到候选集第一个或下一策略。
 *
 * @author: lxcechoo@gmail.com
 */
public interface ModelStrategy {

    /** 策略类型 */
    RouteStrategyType type();

    /**
     * 从候选供应商中选型。
     *
     * @param candidates 已经过熔断过滤的可用候选集（非空）
     * @param ctx        路由上下文
     * @param req        调用请求（部分策略需读取 preferredModel / 预估 token）
     * @return 选中的供应商，{@code null} 表示无法决策
     */
    ModelProvider select(List<ModelProvider> candidates, ModelContext ctx, ModelRequest req);
}
