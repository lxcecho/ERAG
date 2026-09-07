/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.model.impl;

import com.knowledge.ai.model.ModelContext;
import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRequest;
import com.knowledge.ai.model.ModelResponse;
import com.knowledge.ai.model.ModelRouteException;
import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.RouteStrategyType;
import com.knowledge.ai.model.circuit.ProviderCircuitBreaker;
import com.knowledge.ai.model.config.ModelRouterProperties;
import com.knowledge.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型路由编排单测：策略选型 + 熔断过滤 + failover 故障转移。
 */
class ModelRouterImplTest {

    private ModelProvider provider(ProviderType type, boolean available) {
        ModelProvider p = mock(ModelProvider.class);
        when(p.type()).thenReturn(type);
        when(p.available()).thenReturn(available);
        when(p.modelName()).thenReturn(type.getDefaultModel());
        when(p.inputPrice()).thenReturn(BigDecimal.valueOf(0.001));
        when(p.outputPrice()).thenReturn(BigDecimal.valueOf(0.002));
        when(p.maxTokens()).thenReturn(4096);
        return p;
    }

    private ModelRouterProperties props() {
        ModelRouterProperties p = new ModelRouterProperties();
        p.setEnabled(true);
        p.setDefaultStrategy(RouteStrategyType.TENANT);
        p.setFailoverRetries(1);
        return p;
    }

    @Test
    void 正常路由调用成功() {
        ModelProvider openai = provider(ProviderType.OPENAI, true);
        ProviderCircuitBreaker breaker = mock(ProviderCircuitBreaker.class);
        when(breaker.allowRequest(any())).thenReturn(true);
        ModelResponse resp = new ModelResponse("ok", 1, 2, 3, "gpt-4o-mini",
                ProviderType.OPENAI, BigDecimal.ZERO, 10L);
        when(openai.chat(any())).thenReturn(resp);
        // 无策略命中（TENANT 无偏好），回退候选第一个 = openai
        ModelRouterImpl router = new ModelRouterImpl(List.of(openai), List.of(), breaker, props());
        assertSame(resp, router.route(ModelRequest.of("hi"), ModelContext.empty()));
        verify(breaker).recordSuccess(ProviderType.OPENAI);
    }

    @Test
    void 调用失败触发failover到下一候选() {
        ModelProvider openai = provider(ProviderType.OPENAI, true);
        ModelProvider deepseek = provider(ProviderType.DEEPSEEK, true);
        ProviderCircuitBreaker breaker = mock(ProviderCircuitBreaker.class);
        when(breaker.allowRequest(any())).thenReturn(true);
        when(openai.chat(any())).thenThrow(new RuntimeException("openai down"));
        ModelResponse resp = new ModelResponse("ok", 1, 2, 3, "deepseek-chat",
                ProviderType.DEEPSEEK, BigDecimal.ZERO, 10L);
        when(deepseek.chat(any())).thenReturn(resp);
        ModelRouterImpl router = new ModelRouterImpl(List.of(openai, deepseek), List.of(), breaker, props());
        assertSame(resp, router.route(ModelRequest.of("hi"), ModelContext.empty()));
        verify(breaker).recordFailure(ProviderType.OPENAI);
        verify(breaker).recordSuccess(ProviderType.DEEPSEEK);
    }

    @Test
    void 无可用供应商抛异常() {
        ModelProvider openai = provider(ProviderType.OPENAI, false);
        ProviderCircuitBreaker breaker = mock(ProviderCircuitBreaker.class);
        ModelRouterImpl router = new ModelRouterImpl(List.of(openai), List.of(), breaker, props());
        assertThrows(ModelRouteException.class,
                () -> router.route(ModelRequest.of("hi"), ModelContext.empty()));
    }

    @Test
    void 全部熔断抛熔断异常() {
        ModelProvider openai = provider(ProviderType.OPENAI, true);
        ProviderCircuitBreaker breaker = mock(ProviderCircuitBreaker.class);
        when(breaker.allowRequest(any())).thenReturn(false);
        ModelRouterImpl router = new ModelRouterImpl(List.of(openai), List.of(), breaker, props());
        ModelRouteException ex = assertThrows(ModelRouteException.class,
                () -> router.route(ModelRequest.of("hi"), ModelContext.empty()));
        assertEquals(ResultCode.CIRCUIT_BREAKER_OPEN.getCode(), ex.getCode());
    }

    @Test
    void 全部候选调用失败抛异常() {
        ModelProvider openai = provider(ProviderType.OPENAI, true);
        ModelProvider deepseek = provider(ProviderType.DEEPSEEK, true);
        ProviderCircuitBreaker breaker = mock(ProviderCircuitBreaker.class);
        when(breaker.allowRequest(any())).thenReturn(true);
        when(openai.chat(any())).thenThrow(new RuntimeException("down"));
        when(deepseek.chat(any())).thenThrow(new RuntimeException("down"));
        ModelRouterImpl router = new ModelRouterImpl(List.of(openai, deepseek), List.of(), breaker, props());
        assertThrows(ModelRouteException.class,
                () -> router.route(ModelRequest.of("hi"), ModelContext.empty()));
    }
}
