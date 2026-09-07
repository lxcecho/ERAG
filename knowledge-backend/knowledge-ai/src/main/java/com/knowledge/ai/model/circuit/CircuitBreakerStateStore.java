package com.knowledge.ai.model.circuit;

import com.knowledge.ai.model.ProviderType;

/**
 * 熔断状态存储抽象：提供带 TTL 的 KV 读写与自增原语，供 {@link ProviderCircuitBreaker} 实现状态机。
 * <p>
 * 抽象原因：生产用 Redis（多节点共享熔断状态），单测用内存实现（不依赖 Redis）。
 * 两个实现语义等价，{@link ProviderCircuitBreaker} 仅依赖本接口，可在两种存储间无缝切换。
 *
 * @author: lxcechoo@gmail.com
 */
public interface CircuitBreakerStateStore {

    /** 读取值（不存在 / 已过期返回 null） */
    String get(ProviderType type, String key);

    /**
     * 写入值。
     *
     * @param ttlSeconds TTL 秒数，{@code <=0} 表示永久
     */
    void set(ProviderType type, String key, String value, long ttlSeconds);

    /**
     * 自增并返回自增后的值（key 不存在时初始化为 1），首次自增时设置 TTL。
     *
     * @param ttlSeconds TTL 秒数（仅首次创建时生效），{@code <=0} 表示永久
     */
    long increment(ProviderType type, String key, long ttlSeconds);

    /** 删除 key */
    void remove(ProviderType type, String key);
}
