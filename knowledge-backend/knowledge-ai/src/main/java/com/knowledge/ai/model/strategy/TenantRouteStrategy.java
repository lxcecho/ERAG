package com.knowledge.ai.model.strategy;

import com.knowledge.ai.model.ModelContext;
import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRequest;
import com.knowledge.ai.model.ModelStrategy;
import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.RouteStrategyType;
import com.knowledge.ai.model.config.ModelRouterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按租户路由：按 {@code tenantId} 查 {@code ai.model-router.tenant-preference} 偏好供应商映射。
 * <p>用于多租户配额隔离 / 供应商绑定场景：如租户 A 绑定 DeepSeek、租户 B 绑定 OpenAI。
 * <p>无偏好映射（tenantId 为 null 或未配置）返回 null，Router 回退到默认策略。
 *
 * @author: lxcechoo@gmail.com
 */
@Component
@RequiredArgsConstructor
public class TenantRouteStrategy implements ModelStrategy {

    private final ModelRouterProperties properties;

    @Override
    public RouteStrategyType type() {
        return RouteStrategyType.TENANT;
    }

    @Override
    public ModelProvider select(List<ModelProvider> candidates, ModelContext ctx, ModelRequest req) {
        Long tenantId = ctx.tenantId();
        if (tenantId == null) {
            return null;
        }
        ProviderType preferred = properties.getTenantPreference().get(tenantId);
        if (preferred == null) {
            return null;
        }
        return candidates.stream()
                .filter(p -> p.type() == preferred)
                .findFirst()
                .orElse(null);
    }
}
