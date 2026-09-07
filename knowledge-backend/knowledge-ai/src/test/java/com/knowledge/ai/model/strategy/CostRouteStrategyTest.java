/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.model.strategy;

import com.knowledge.ai.model.ModelContext;
import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRequest;
import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.RouteStrategyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 按成本路由策略单测。
 */
class CostRouteStrategyTest {

    private final CostRouteStrategy strategy = new CostRouteStrategy();

    private ModelProvider provider(ProviderType type, double in, double out) {
        ModelProvider p = mock(ModelProvider.class);
        when(p.type()).thenReturn(type);
        when(p.inputPrice()).thenReturn(BigDecimal.valueOf(in));
        when(p.outputPrice()).thenReturn(BigDecimal.valueOf(out));
        when(p.maxTokens()).thenReturn(4096);
        return p;
    }

    @Test
    void 选最低成本供应商() {
        ModelProvider expensive = provider(ProviderType.OPENAI, 0.01, 0.03);
        ModelProvider cheap = provider(ProviderType.DEEPSEEK, 0.001, 0.002);
        ModelContext ctx = ModelContext.of(null, RouteStrategyType.COST);
        assertSame(cheap, strategy.select(List.of(expensive, cheap), ctx, ModelRequest.of("hi")));
    }

    @Test
    void 超预算供应商被排除() {
        // cheap cost ≈ 0.001025，expensive cost ≈ 0.01537；budget=0.005 排除 expensive
        ModelProvider expensive = provider(ProviderType.OPENAI, 0.01, 0.03);
        ModelProvider cheap = provider(ProviderType.DEEPSEEK, 0.001, 0.002);
        ModelContext ctx = new ModelContext(null, RouteStrategyType.COST, null, null, new BigDecimal("0.005"), null);
        assertSame(cheap, strategy.select(List.of(expensive, cheap), ctx, ModelRequest.of("hi")));
    }

    @Test
    void 全部超预算返回null() {
        ModelProvider expensive = provider(ProviderType.OPENAI, 0.01, 0.03);
        ModelContext ctx = new ModelContext(null, RouteStrategyType.COST, null, null, new BigDecimal("0.0001"), null);
        assertNull(strategy.select(List.of(expensive), ctx, ModelRequest.of("hi")));
    }
}
