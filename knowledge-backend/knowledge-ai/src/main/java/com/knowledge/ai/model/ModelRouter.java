package com.knowledge.ai.model;

import java.util.List;

/**
 * 模型路由中心：统一入口，按上下文策略选择供应商并执行调用。
 * <p>
 * 编排流程（见 {@code ModelRouterImpl}）：
 * <ol>
 *   <li>收集可用供应商 → 过滤熔断 OPEN 状态者</li>
 *   <li>按 {@link ModelContext#strategy()}（或默认策略）分发到对应 {@link ModelStrategy}</li>
 *   <li>策略选型 → 调用 → 成功记录熔断 success；失败记录 failure 并按 failover 重试下一候选</li>
 * </ol>
 * 熔断与 failover 保证单供应商故障不影响整体可用性。
 *
 * @author: lxcechoo@gmail.com
 */
public interface ModelRouter {

    /**
     * 路由并执行调用。
     *
     * @param req 调用请求
     * @param ctx 路由上下文（tenantId 缺省由 Router 从 TenantContext 补全）
     * @return 模型响应
     * @throws ModelRouteException 无可用供应商或全部失败时抛出
     */
    ModelResponse route(ModelRequest req, ModelContext ctx);

    /** 当前所有可用供应商（供监控 / 调试） */
    List<ModelProvider> availableProviders();
}
