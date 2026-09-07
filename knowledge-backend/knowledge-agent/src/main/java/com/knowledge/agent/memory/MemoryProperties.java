package com.knowledge.agent.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆中心配置（读取 application.yml 中 memory.* 配置）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    /** 会话记忆窗口（最近 N 条消息，含 user+assistant） */
    private int conversationWindow = 10;

    /** 会话摘要触发阈值（会话轮次 ≥ 此值时异步生成摘要，1 轮 = 1 user + 1 assistant） */
    private int summaryThreshold = 10;

    /** 长期事实抽取触发阈值（会话轮次 ≥ 此值时异步抽取） */
    private int longTermThreshold = 10;

    /** 向量召回 Top K */
    private int recallTopK = 5;

    /** 向量召回相似度下限（0~1） */
    private double vectorMinScore = 0.6;

    /** 会话记忆 Redis 缓存 TTL（秒） */
    private long convCacheTtlSeconds = 3600L;

    /** 向量记忆独立 Milvus 集合名 */
    private String collectionName = "agent_memory_vec";

    /** 记忆异步任务线程池大小（摘要/事实抽取用） */
    private int taskPoolSize = 2;

    /**
     * 长期记忆衰减：加载时最多返回的事实条数。
     * <p>旧事实不会被物理删除，但随着新事实积累自然被挤出加载窗口，实现隐式衰减。
     * 默认 30 条，覆盖用户近期最重要的偏好和事实。
     */
    private int maxLongTermFacts = 30;

    /**
     * 长期记忆保留天数（超过此天数的旧记忆由定时任务清理）。
     * <p>0 表示不清理（永久保留）。建议生产环境设为 180 天。
     */
    private int retentionDays = 180;
}
