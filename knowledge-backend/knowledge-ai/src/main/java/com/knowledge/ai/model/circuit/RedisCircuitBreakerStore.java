package com.knowledge.ai.model.circuit;

import com.knowledge.ai.model.ProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的熔断状态存储（生产实现）。
 * <p>多节点共享熔断状态：任一节点标记某供应商 OPEN 后，全集群立即对该供应商熔断。
 * <p>key 规则：{@code model-router:cb:<provider>:<field>}，与 RAG/统计等缓存隔离。
 * <p>容错：Redis 不可用时 get 返回 null（视为 CLOSED 放行）、increment 返回 0（不计数），
 * 避免熔断器自身故障拖垮主流程——熔断是保护手段，不可成为单点。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
public class RedisCircuitBreakerStore implements CircuitBreakerStateStore {

    private static final String PREFIX = "model-router:cb:";

    private final StringRedisTemplate redis;

    public RedisCircuitBreakerStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(ProviderType type, String key) {
        return PREFIX + type.name().toLowerCase() + ":" + key;
    }

    @Override
    public String get(ProviderType type, String key) {
        try {
            return redis.opsForValue().get(key(type, key));
        } catch (Exception e) {
            log.warn("[CircuitBreaker-Redis] get 失败 {} : {}，降级为 null（视为 CLOSED）", type, key, e);
            return null;
        }
    }

    @Override
    public void set(ProviderType type, String key, String value, long ttlSeconds) {
        try {
            String k = key(type, key);
            if (ttlSeconds > 0) {
                redis.opsForValue().set(k, value, ttlSeconds, TimeUnit.SECONDS);
            } else {
                redis.opsForValue().set(k, value);
            }
        } catch (Exception e) {
            log.warn("[CircuitBreaker-Redis] set 失败 {} : {}", type, key, e);
        }
    }

    @Override
    public long increment(ProviderType type, String key, long ttlSeconds) {
        try {
            String k = key(type, key);
            Long n = redis.opsForValue().increment(k);
            long v = n == null ? 0 : n;
            // 首次自增时设置 TTL（统计窗口），避免计数永不过期
            if (v == 1 && ttlSeconds > 0) {
                redis.expire(k, ttlSeconds, TimeUnit.SECONDS);
            }
            return v;
        } catch (Exception e) {
            log.warn("[CircuitBreaker-Redis] increment 失败 {} : {}，返回 0（不计数）", type, key, e);
            return 0;
        }
    }

    @Override
    public void remove(ProviderType type, String key) {
        try {
            redis.delete(key(type, key));
        } catch (Exception e) {
            log.warn("[CircuitBreaker-Redis] remove 失败 {} : {}", type, key, e);
        }
    }
}
