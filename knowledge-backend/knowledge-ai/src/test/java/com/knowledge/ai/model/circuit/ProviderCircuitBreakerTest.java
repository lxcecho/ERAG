/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.model.circuit;

import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.config.ModelRouterProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 供应商熔断器状态机单测（基于 InMemoryCircuitBreakerStore，不依赖 Redis）。
 */
class ProviderCircuitBreakerTest {

    private ModelRouterProperties.CircuitBreaker cbConfig() {
        ModelRouterProperties.CircuitBreaker c = new ModelRouterProperties.CircuitBreaker();
        c.setEnabled(true);
        c.setFailureThreshold(3);
        c.setWindowSeconds(60);
        c.setOpenSeconds(1);   // 短冷却，便于测 HALF_OPEN 转换
        c.setHalfOpenPermits(1);
        return c;
    }

    @Test
    void CLOSED失败达阈值转OPEN() {
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(new InMemoryCircuitBreakerStore(), cbConfig());
        cb.recordFailure(ProviderType.OPENAI);
        cb.recordFailure(ProviderType.OPENAI);
        assertTrue(cb.allowRequest(ProviderType.OPENAI));   // 2 < 3，仍 CLOSED
        cb.recordFailure(ProviderType.OPENAI);
        assertEquals(ProviderCircuitBreaker.OPEN, cb.stateOf(ProviderType.OPENAI));
        assertFalse(cb.allowRequest(ProviderType.OPENAI));  // OPEN 拒绝
    }

    @Test
    void OPEN冷却到期转HALF_OPEN放行() throws InterruptedException {
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(new InMemoryCircuitBreakerStore(), cbConfig());
        cb.recordFailure(ProviderType.OPENAI);
        cb.recordFailure(ProviderType.OPENAI);
        cb.recordFailure(ProviderType.OPENAI);
        assertFalse(cb.allowRequest(ProviderType.OPENAI));   // OPEN
        Thread.sleep(1100);                                   // 等冷却（openSeconds=1）
        assertTrue(cb.allowRequest(ProviderType.OPENAI));    // HALF_OPEN 放行
        assertEquals(ProviderCircuitBreaker.HALF_OPEN, cb.stateOf(ProviderType.OPENAI));
    }

    @Test
    void HALF_OPEN成功转CLOSED() {
        InMemoryCircuitBreakerStore store = new InMemoryCircuitBreakerStore();
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(store, cbConfig());
        store.set(ProviderType.OPENAI, "state", ProviderCircuitBreaker.HALF_OPEN, 0);
        cb.recordSuccess(ProviderType.OPENAI);
        assertEquals(ProviderCircuitBreaker.CLOSED, cb.stateOf(ProviderType.OPENAI));
        assertTrue(cb.allowRequest(ProviderType.OPENAI));
    }

    @Test
    void HALF_OPEN失败转OPEN() {
        InMemoryCircuitBreakerStore store = new InMemoryCircuitBreakerStore();
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(store, cbConfig());
        store.set(ProviderType.OPENAI, "state", ProviderCircuitBreaker.HALF_OPEN, 0);
        cb.recordFailure(ProviderType.OPENAI);
        assertEquals(ProviderCircuitBreaker.OPEN, cb.stateOf(ProviderType.OPENAI));
    }

    @Test
    void 熔断关闭时恒放行不记录() {
        ModelRouterProperties.CircuitBreaker c = cbConfig();
        c.setEnabled(false);
        ProviderCircuitBreaker cb = new ProviderCircuitBreaker(new InMemoryCircuitBreakerStore(), c);
        cb.recordFailure(ProviderType.OPENAI);
        cb.recordFailure(ProviderType.OPENAI);
        cb.recordFailure(ProviderType.OPENAI);
        assertTrue(cb.allowRequest(ProviderType.OPENAI));    // 关闭，恒放行
        assertNull(cb.stateOf(ProviderType.OPENAI));         // 不记录状态
    }
}
