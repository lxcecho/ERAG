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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 按模型名称路由策略单测。
 */
class NameRouteStrategyTest {

    private final NameRouteStrategy strategy = new NameRouteStrategy();

    private ModelProvider provider(ProviderType type, String modelName) {
        ModelProvider p = mock(ModelProvider.class);
        when(p.type()).thenReturn(type);
        when(p.modelName()).thenReturn(modelName);
        return p;
    }

    @Test
    void 精确匹配模型名() {
        ModelProvider openai = provider(ProviderType.OPENAI, "gpt-4o-mini");
        ModelProvider deepseek = provider(ProviderType.DEEPSEEK, "deepseek-chat");
        ModelContext ctx = new ModelContext(null, RouteStrategyType.NAME, "gpt-4o-mini", null, null, null);
        ModelProvider selected = strategy.select(List.of(openai, deepseek), ctx, ModelRequest.of("hi"));
        assertSame(openai, selected);
    }

    @Test
    void 包含匹配模型名() {
        ModelProvider openai = provider(ProviderType.OPENAI, "gpt-4o-mini");
        ModelContext ctx = new ModelContext(null, RouteStrategyType.NAME, "gpt-4o", null, null, null);
        assertSame(openai, strategy.select(List.of(openai), ctx, ModelRequest.of("hi")));
    }

    @Test
    void 无匹配返回null() {
        ModelProvider openai = provider(ProviderType.OPENAI, "gpt-4o-mini");
        ModelContext ctx = new ModelContext(null, RouteStrategyType.NAME, "claude", null, null, null);
        assertNull(strategy.select(List.of(openai), ctx, ModelRequest.of("hi")));
    }

    @Test
    void preferredModel为空返回null() {
        ModelProvider openai = provider(ProviderType.OPENAI, "gpt-4o-mini");
        assertNull(strategy.select(List.of(openai), ModelContext.empty(), ModelRequest.of("hi")));
    }

    @Test
    void 回退到请求级preferredModel() {
        ModelProvider openai = provider(ProviderType.OPENAI, "gpt-4o-mini");
        ModelRequest req = new ModelRequest(null, "hi", null, null, "gpt-4o-mini");
        assertSame(openai, strategy.select(List.of(openai), ModelContext.empty(), req));
    }
}
