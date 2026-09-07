package com.knowledge.ai.model.circuit;

import com.knowledge.ai.model.ProviderType;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的熔断状态存储（单测 / 单节点兜底实现）。
 * <p>语义与 {@link RedisCircuitBreakerStore} 等价：支持 TTL 过期与原子自增
 * （{@link ConcurrentHashMap#compute} 保证线程安全）。
 * <p>当容器中无 {@link StringRedisTemplate}（如未启用 Redis）时，由配置类兜底装配本实现，
 * 使 ModelRouter 在无 Redis 环境也能运行（仅熔断状态不跨节点共享）。
 *
 * @author: lxcechoo@gmail.com
 */
public class InMemoryCircuitBreakerStore implements CircuitBreakerStateStore {

    /** 存储条目：value + 过期时间戳（0 表示永久） */
    private record Entry(String value, long expireAt) {
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    private String key(ProviderType type, String key) {
        return type.name() + ":" + key;
    }

    @Override
    public String get(ProviderType type, String key) {
        String k = key(type, key);
        Entry e = map.get(k);
        if (e == null) {
            return null;
        }
        if (e.expireAt > 0 && System.currentTimeMillis() > e.expireAt) {
            map.remove(k, e);
            return null;
        }
        return e.value;
    }

    @Override
    public void set(ProviderType type, String key, String value, long ttlSeconds) {
        long expireAt = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000L : 0L;
        map.put(key(type, key), new Entry(value, expireAt));
    }

    @Override
    public long increment(ProviderType type, String key, long ttlSeconds) {
        String k = key(type, key);
        long now = System.currentTimeMillis();
        long[] holder = new long[1];
        map.compute(k, (kk, e) -> {
            long cur = 0;
            if (e != null && (e.expireAt == 0 || now <= e.expireAt)) {
                cur = parseLong(e.value);
            }
            long next = cur + 1;
            long expireAt = ttlSeconds > 0 ? now + ttlSeconds * 1000L : 0L;
            holder[0] = next;
            return new Entry(String.valueOf(next), expireAt);
        });
        return holder[0];
    }

    @Override
    public void remove(ProviderType type, String key) {
        map.remove(key(type, key));
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
