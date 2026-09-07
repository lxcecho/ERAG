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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 按 Token 数路由策略单测。
 */
class TokenRouteStrategyTest {

    private final TokenRouteStrategy strategy = new TokenRouteStrategy();

    private ModelProvider provider(ProviderType type, double in, double out, int maxTokens) {
        ModelProvider p = mock(ModelProvider.class);
        when(p.type()).thenReturn(type);
        when(p.inputPrice()).thenReturn(BigDecimal.valueOf(in));
        when(p.outputPrice()).thenReturn(BigDecimal.valueOf(out));
        when(p.maxTokens()).thenReturn(maxTokens);
        return p;
    }

    @Test
    void 容量不足跳过选容量充足者() {
        // need = estPrompt(1) + maxTokens(2000) ≈ 2001；small(1024) 不足，large(4096) 充足
        ModelProvider small = provider(ProviderType.OLLAMA, 0.001, 0.001, 1024);
        ModelProvider large = provider(ProviderType.OPENAI, 0.01, 0.03, 4096);
        ModelRequest req = new ModelRequest(null, "hi", 2000, null, null);
        assertSame(large, strategy.select(List.of(small, large), ModelContext.of(null, RouteStrategyType.TOKEN), req));
    }

    @Test
    void 容量充足选最经济() {
        ModelProvider cheap = provider(ProviderType.DEEPSEEK, 0.001, 0.002, 4096);
        ModelProvider expensive = provider(ProviderType.OPENAI, 0.01, 0.03, 4096);
        ModelRequest req = new ModelRequest(null, "hi", 2000, null, null);
        assertSame(cheap, strategy.select(List.of(cheap, expensive), ModelContext.of(null, RouteStrategyType.TOKEN), req));
    }

    @Test
    void 全部容量不足选容量最大兜底() {
        // need ≈ 10001，都不足
        ModelProvider small = provider(ProviderType.OLLAMA, 0.001, 0.001, 1024);
        ModelProvider mid = provider(ProviderType.DEEPSEEK, 0.001, 0.002, 2048);
        ModelRequest req = new ModelRequest(null, "hi", 10000, null, null);
        assertSame(mid, strategy.select(List.of(small, mid), ModelContext.of(null, RouteStrategyType.TOKEN), req));
    }

    @Test
    void 超预算跳过回退容量最大() {
        // budget=1000 < need≈2001，feasible 空，回退选容量最大
        ModelProvider ok = provider(ProviderType.DEEPSEEK, 0.001, 0.002, 4096);
        ModelRequest req = new ModelRequest(null, "hi", 2000, null, null);
        ModelContext ctx = new ModelContext(null, RouteStrategyType.TOKEN, null, 1000, null, null);
        assertSame(ok, strategy.select(List.of(ok), ctx, req));
    }
}
