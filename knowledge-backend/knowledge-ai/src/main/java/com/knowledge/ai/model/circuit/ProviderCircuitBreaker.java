package com.knowledge.ai.model.circuit;

import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.config.ModelRouterProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 供应商级熔断器：三态状态机 CLOSED → OPEN → HALF_OPEN → CLOSED。
 * <p>
 * 状态机：
 * <ul>
 *   <li><b>CLOSED</b>（正常）：放行所有请求；失败累计计数，达 {@code failureThreshold} 转 OPEN</li>
 *   <li><b>OPEN</b>（熔断）：拒绝请求；冷却 {@code openSeconds} 后转 HALF_OPEN</li>
 *   <li><b>HALF_OPEN</b>（半开）：放行 {@code halfOpenPermits} 个试探请求；
 *       成功 → CLOSED，失败 → OPEN</li>
 * </ul>
 * <p>
 * 存储解耦：状态/计数/时间戳持久于 {@link CircuitBreakerStateStore}（Redis 或内存），
 * 本类只含状态机逻辑，可用内存存储纯单测，不依赖 Redis。
 * <p>
 * 容错：{@code enabled=false} 时 {@link #allowRequest} 恒 true、不记录成败（熔断关闭，路由照常）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
public class ProviderCircuitBreaker {

    public static final String CLOSED = "CLOSED";
    public static final String OPEN = "OPEN";
    public static final String HALF_OPEN = "HALF_OPEN";

    private static final String STATE_KEY = "state";
    private static final String FAILURES_KEY = "failures";
    private static final String OPEN_SINCE_KEY = "openSince";
    private static final String HALF_PERMIT_KEY = "halfPermit";

    private final CircuitBreakerStateStore store;
    private final ModelRouterProperties.CircuitBreaker config;

    public ProviderCircuitBreaker(CircuitBreakerStateStore store, ModelRouterProperties.CircuitBreaker config) {
        this.store = store;
        this.config = config;
    }

    /**
     * 是否放行请求。
     * <p>OPEN 冷却到期会自动转 HALF_OPEN 并放行试探；HALF_OPEN 按 permit 限流。
     */
    public boolean allowRequest(ProviderType type) {
        if (!config.isEnabled()) {
            return true;
        }
        String state = stateOf(type);
        if (state == null || CLOSED.equals(state)) {
            return true;
        }
        if (OPEN.equals(state)) {
            // 冷却到期 → 转 HALF_OPEN 放行试探
            long since = parseLong(store.get(type, OPEN_SINCE_KEY));
            if (System.currentTimeMillis() - since >= config.getOpenSeconds() * 1000L) {
                store.set(type, STATE_KEY, HALF_OPEN, 0);
                store.remove(type, HALF_PERMIT_KEY);
                log.info("[CircuitBreaker] {} OPEN 冷却到期，转 HALF_OPEN 试探", type);
                return tryAcquireHalfPermit(type);
            }
            return false;
        }
        if (HALF_OPEN.equals(state)) {
            return tryAcquireHalfPermit(type);
        }
        return true;
    }

    /** 调用成功：HALF_OPEN/OPEN 恢复 CLOSED，清零计数 */
    public void recordSuccess(ProviderType type) {
        if (!config.isEnabled()) {
            return;
        }
        String state = stateOf(type);
        if (HALF_OPEN.equals(state) || OPEN.equals(state)) {
            toClosed(type);
            log.info("[CircuitBreaker] {} 恢复 CLOSED", type);
        }
    }

    /** 调用失败：HALF_OPEN 立即转 OPEN；CLOSED 累计达阈值转 OPEN */
    public void recordFailure(ProviderType type) {
        if (!config.isEnabled()) {
            return;
        }
        String state = stateOf(type);
        if (HALF_OPEN.equals(state)) {
            toOpen(type);
            log.warn("[CircuitBreaker] {} HALF_OPEN 试探失败，转 OPEN", type);
            return;
        }
        long count = store.increment(type, FAILURES_KEY, config.getWindowSeconds());
        if (count >= config.getFailureThreshold()) {
            toOpen(type);
            log.warn("[CircuitBreaker] {} 失败 {} 次达阈值，转 OPEN", type, count);
        }
    }

    /** 当前状态（null 视为 CLOSED） */
    public String stateOf(ProviderType type) {
        return store.get(type, STATE_KEY);
    }

    /* ==================== 内部状态转换 ==================== */

    private void toOpen(ProviderType type) {
        store.set(type, STATE_KEY, OPEN, 0);
        store.set(type, OPEN_SINCE_KEY, String.valueOf(System.currentTimeMillis()), 0);
        store.remove(type, HALF_PERMIT_KEY);
    }

    private void toClosed(ProviderType type) {
        store.set(type, STATE_KEY, CLOSED, 0);
        store.remove(type, FAILURES_KEY);
        store.remove(type, OPEN_SINCE_KEY);
        store.remove(type, HALF_PERMIT_KEY);
    }

    /** 半开试探许可：自增 permit 计数，仅前 N 个放行（TTL 防泄漏） */
    private boolean tryAcquireHalfPermit(ProviderType type) {
        long n = store.increment(type, HALF_PERMIT_KEY, config.getOpenSeconds());
        return n <= config.getHalfOpenPermits();
    }

    private static long parseLong(String s) {
        if (s == null) {
            return 0L;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
