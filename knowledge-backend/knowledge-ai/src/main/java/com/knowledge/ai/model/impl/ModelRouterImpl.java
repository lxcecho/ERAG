package com.knowledge.ai.model.impl;

import com.knowledge.ai.model.ModelContext;
import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRequest;
import com.knowledge.ai.model.ModelResponse;
import com.knowledge.ai.model.ModelRouteException;
import com.knowledge.ai.model.ModelRouter;
import com.knowledge.ai.model.ModelStrategy;
import com.knowledge.ai.model.RouteStrategyType;
import com.knowledge.ai.model.circuit.ProviderCircuitBreaker;
import com.knowledge.ai.model.config.ModelRouterProperties;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型路由实现：策略选型 + 熔断过滤 + failover 故障转移。
 * <p>
 * 编排流程：
 * <ol>
 *   <li><b>收集可用供应商</b>：{@link ModelProvider#available()} 过滤配置未启用 / apiKey 缺失者</li>
 *   <li><b>熔断过滤</b>：排除 {@link ProviderCircuitBreaker#allowRequest} 拒绝（OPEN 状态）者</li>
 *   <li><b>策略选型</b>：按 {@link ModelContext#strategy()}（或默认策略）分发到 {@link ModelStrategy}，
 *       策略返回 null 时回退到候选第一个（可用性优先）</li>
 *   <li><b>调用 + failover</b>：调用选中供应商，失败则记录熔断 failure 并按 {@code failoverRetries}
 *       依次尝试下一个未试且未熔断的候选；全部失败抛 {@link ModelRouteException}</li>
 * </ol>
 * <p>租户补全：{@link ModelContext#tenantId()} 缺省时从 {@link TenantContext} 取，支持异步场景显式传入。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@RequiredArgsConstructor
public class ModelRouterImpl implements ModelRouter {

    private final List<ModelProvider> providers;
    private final List<ModelStrategy> strategies;
    private final ProviderCircuitBreaker breaker;
    private final ModelRouterProperties properties;

    @Override
    public ModelResponse route(ModelRequest req, ModelContext ctx) {
        ModelContext normalized = normalize(ctx);

        List<ModelProvider> available = availableProviders();
        if (available.isEmpty()) {
            throw new ModelRouteException("无可用模型供应商，请检查 ai.model-router.providers 配置");
        }

        List<ModelProvider> candidates = available.stream()
                .filter(p -> breaker.allowRequest(p.type()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            throw new ModelRouteException(ResultCode.CIRCUIT_BREAKER_OPEN, "所有模型供应商熔断中，请稍后再试");
        }

        RouteStrategyType strategyType = normalized.strategy() != null
                ? normalized.strategy() : properties.getDefaultStrategy();
        ModelProvider selected = selectByStrategy(strategyType, candidates, normalized, req);
        if (selected == null) {
            // 策略无法决策，回退到候选第一个（可用性优先于精确匹配）
            selected = candidates.get(0);
            log.debug("[ModelRouter] 策略 {} 无法决策，回退到 {}", strategyType, selected.type());
        }
        log.info("[ModelRouter] 路由命中 {}（策略={}，候选 {} 个）", selected.type(), strategyType, candidates.size());
        return invokeWithFailover(selected, candidates, req);
    }

    @Override
    public List<ModelProvider> availableProviders() {
        return providers.stream()
                .filter(ModelProvider::available)
                .collect(Collectors.toList());
    }

    /* ==================== failover 调用 ==================== */

    private ModelResponse invokeWithFailover(ModelProvider primary, List<ModelProvider> candidates, ModelRequest req) {
        List<ModelProvider> tried = new ArrayList<>();
        ModelProvider current = primary;
        RuntimeException lastError = null;
        int maxAttempts = properties.getFailoverRetries() + 1;

        for (int attempt = 0; attempt < maxAttempts && current != null; attempt++) {
            tried.add(current);
            try {
                ModelResponse resp = current.chat(req);
                breaker.recordSuccess(current.type());
                return resp;
            } catch (RuntimeException e) {
                breaker.recordFailure(current.type());
                log.warn("[ModelRouter] 供应商 {} 调用失败(attempt={}/{}): {}",
                        current.type(), attempt + 1, maxAttempts, e.getMessage());
                lastError = e;
                current = nextCandidate(candidates, tried);
            }
        }
        throw new ModelRouteException("所有候选供应商调用失败: "
                + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    /** 选下一个未试且未被熔断的候选 */
    private ModelProvider nextCandidate(List<ModelProvider> candidates, List<ModelProvider> tried) {
        for (ModelProvider p : candidates) {
            if (!tried.contains(p) && breaker.allowRequest(p.type())) {
                return p;
            }
        }
        return null;
    }

    /* ==================== 策略分发 ==================== */

    private ModelProvider selectByStrategy(RouteStrategyType type, List<ModelProvider> candidates,
                                           ModelContext ctx, ModelRequest req) {
        return strategies.stream()
                .filter(s -> s.type() == type)
                .findFirst()
                .map(s -> s.select(candidates, ctx, req))
                .orElse(null);
    }

    /* ==================== 上下文补全 ==================== */

    private ModelContext normalize(ModelContext ctx) {
        if (ctx == null) {
            return ModelContext.empty();
        }
        if (ctx.tenantId() == null) {
            Long tid = TenantContext.getTenantId();
            if (tid != null) {
                return new ModelContext(tid, ctx.strategy(), ctx.preferredModel(),
                        ctx.tokenBudget(), ctx.costBudget(), ctx.scenario());
            }
        }
        return ctx;
    }
}
