package com.knowledge.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 全局配置：序列化 + 缓存管理器（多 TTL 策略）
 *
 * 降级：spring.cache.type=none 时跳过 @EnableCaching 注解效果（不创建 CacheManager），
 *       应用仍可正常启动（无 Redis 环境开发/演示场景）。
 *
 * @author: lxcechoo@gmail.com
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = false)
public class CommonRedisConfig {

    /** 默认缓存过期时间：30 分钟 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    /** Prompt 模板缓存过期时间：1 小时（模板变更低频） */
    public static final Duration PROMPT_TTL = Duration.ofHours(1);
    /** AI 调用统计缓存过期时间：5 分钟（统计时效性高） */
    public static final Duration STATS_TTL = Duration.ofMinutes(5);
    /** RAG 检索结果缓存过期时间：10 分钟 */
    public static final Duration SEARCH_TTL = Duration.ofMinutes(10);

    /**
     * 构造带类型信息 + Java8 时间支持的 ObjectMapper，供 RedisTemplate 与 CacheManager 共用。
     * <p>默认 GenericJackson2JsonRedisSerializer 构造器对 LocalDateTime/LocalDate 的支持依赖运行时
     * 版本，实测在当前栈下未自动注册 jsr310，导致含日期字段的 VO（如 PromptTemplateVO）缓存写入抛
     * SerializationException。此处显式注册 JavaTimeModule 彻底解决。
     */
    private static ObjectMapper buildRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * RedisTemplate：key 用 String 序列化，value 用 GenericJackson2JsonRedisSerializer
     * 写入 Redis 的数据带 @class 类型信息，反序列化时自动恢复 POJO
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer jacksonSerializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());
        StringRedisSerializer stringSerializer = StringRedisSerializer.UTF_8;

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jacksonSerializer);
        template.setHashValueSerializer(jacksonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * RedisCacheManager：多 TTL 策略
     * - 未指定 cacheName → DEFAULT_TTL（30 分钟）
     * - prompt:* → PROMPT_TTL（1 小时）
     * - stats:* → STATS_TTL（5 分钟）
     * - search:* → SEARCH_TTL（10 分钟）
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper())))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("prompt", defaultConfig.entryTtl(PROMPT_TTL));
        cacheConfigs.put("kb", defaultConfig.entryTtl(DEFAULT_TTL));
        cacheConfigs.put("stats", defaultConfig.entryTtl(STATS_TTL));
        cacheConfigs.put("search", defaultConfig.entryTtl(SEARCH_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}