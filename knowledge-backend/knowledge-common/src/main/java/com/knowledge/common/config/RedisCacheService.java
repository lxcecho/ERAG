package com.knowledge.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 缓存操作封装：提供 get-or-compute / set / delete 便捷方法
 *
 * 设计原则：
 * - 所有方法 best-effort（Redis 不可用时静默降级为 direct 模式）
 * - 不抛异常阻塞主流程
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = false)
public class RedisCacheService {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisCacheService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取缓存，未命中时执行 supplier 并写入缓存
     *
     * @param key    缓存键（会自动加 erag: 前缀避免冲突）
     * @param ttl    过期时间
     * @param loader 数据加载函数
     * @return 缓存数据或实时加载结果
     */
    public String getOrCompute(String key, Duration ttl, Supplier<String> loader) {
        String fullKey = "erag:" + key;
        try {
            String cached = stringRedisTemplate.opsForValue().get(fullKey);
            if (cached != null) {
                log.debug("[RedisCache] 命中 key={}", fullKey);
                return cached;
            }
            String value = loader.get();
            if (value != null) {
                stringRedisTemplate.opsForValue().set(fullKey, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
                log.debug("[RedisCache] 写入 key={} ttl={}s", fullKey, ttl.toSeconds());
            }
            return value;
        } catch (Exception e) {
            log.warn("[RedisCache] 降级直通 key={} err={}", fullKey, e.getMessage());
            return loader.get();
        }
    }

    /**
     * 设置缓存
     */
    public void set(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set("erag:" + key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[RedisCache] 写入失败 key={} err={}", key, e.getMessage());
        }
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        try {
            stringRedisTemplate.delete("erag:" + key);
        } catch (Exception e) {
            log.warn("[RedisCache] 删除失败 key={} err={}", key, e.getMessage());
        }
    }

    /**
     * 尝试获取分布式锁（SETNX + TTL）
     * <p>best-effort：Redis 不可用时返回 false（降级为无锁，不阻断业务）
     *
     * @param lockKey    锁键（会自动加 erag:lock: 前缀）
     * @param ttlSeconds 锁过期时间（秒），防止死锁
     * @return true=获取成功，false=获取失败（已被占用或 Redis 不可用）
     */
    public boolean tryLock(String lockKey, long ttlSeconds) {
        try {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent("erag:lock:" + lockKey, "1", ttlSeconds, TimeUnit.SECONDS);
            boolean acquired = Boolean.TRUE.equals(ok);
            if (!acquired) {
                log.debug("[RedisLock] 获取失败 key={}", lockKey);
            }
            return acquired;
        } catch (Exception e) {
            log.warn("[RedisLock] 获取异常 key={} err={}（降级为无锁）", lockKey, e.getMessage());
            return false;
        }
    }

    /**
     * 释放分布式锁
     * <p>best-effort：Redis 不可用时静默忽略
     *
     * @param lockKey 锁键（会自动加 erag:lock: 前缀）
     */
    public void unlock(String lockKey) {
        try {
            stringRedisTemplate.delete("erag:lock:" + lockKey);
        } catch (Exception e) {
            log.warn("[RedisLock] 释放异常 key={} err={}", lockKey, e.getMessage());
        }
    }

    /**
     * 模式匹配删除（谨慎使用，keys 操作在 Redis 大数据量时可能阻塞）
     */
    public void deleteByPattern(String pattern) {
        try {
            var keys = stringRedisTemplate.keys("erag:" + pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.debug("[RedisCache] 批量删除 pattern={} count={}", pattern, keys.size());
            }
        } catch (Exception e) {
            log.warn("[RedisCache] 批量删除失败 pattern={} err={}", pattern, e.getMessage());
        }
    }
}