/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.model.strategy;

import com.knowledge.ai.model.ModelContext;
import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRequest;
import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.RouteStrategyType;
import com.knowledge.ai.model.config.ModelRouterProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 按租户路由策略单测。
 */
class TenantRouteStrategyTest {

    private ModelProvider provider(ProviderType type) {
        ModelProvider p = mock(ModelProvider.class);
        when(p.type()).thenReturn(type);
        return p;
    }

    @Test
    void 有偏好命中() {
        ModelRouterProperties props = new ModelRouterProperties();
        props.getTenantPreference().put(1L, ProviderType.DEEPSEEK);
        TenantRouteStrategy strategy = new TenantRouteStrategy(props);
        ModelProvider deepseek = provider(ProviderType.DEEPSEEK);
        ModelProvider openai = provider(ProviderType.OPENAI);
        ModelContext ctx = ModelContext.of(1L, RouteStrategyType.TENANT);
        assertSame(deepseek, strategy.select(List.of(openai, deepseek), ctx, ModelRequest.of("hi")));
    }

    @Test
    void 无偏好返回null() {
        TenantRouteStrategy strategy = new TenantRouteStrategy(new ModelRouterProperties());
        assertNull(strategy.select(List.of(provider(ProviderType.OPENAI)),
                ModelContext.of(1L, RouteStrategyType.TENANT), ModelRequest.of("hi")));
    }

    @Test
    void tenantId为null返回null() {
        ModelRouterProperties props = new ModelRouterProperties();
        props.getTenantPreference().put(1L, ProviderType.OPENAI);
        TenantRouteStrategy strategy = new TenantRouteStrategy(props);
        assertNull(strategy.select(List.of(provider(ProviderType.OPENAI)),
                ModelContext.empty(), ModelRequest.of("hi")));
    }

    @Test
    void 偏好供应商不在候选中返回null() {
        ModelRouterProperties props = new ModelRouterProperties();
        props.getTenantPreference().put(1L, ProviderType.CLAUDE);
        TenantRouteStrategy strategy = new TenantRouteStrategy(props);
        assertNull(strategy.select(List.of(provider(ProviderType.OPENAI)),
                ModelContext.of(1L, RouteStrategyType.TENANT), ModelRequest.of("hi")));
    }
}
