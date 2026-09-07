package com.knowledge.ai.health;

import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Milvus 向量库健康检查
 * <p><b>不主动 ping Milvus</b>（LangChain4j EmbeddingStore 无轻量 ping 接口，且主动搜索会烧 embedding token），
 * 改为读取 {@link AiCallHealthTracker} 中 "milvus" 资源的滑动窗口错误率被动判定。
 * <p>{@code @ConditionalOnBean(EmbeddingStore.class)}：EmbeddingStore 未装配时此指标不注册（Milvus 未启用场景）。
 *
 * @author: lxcechoo@gmail.com
 */
@Component("milvusHealthIndicator")
@ConditionalOnBean(EmbeddingStore.class)
public class MilvusHealthIndicator implements HealthIndicator {

    private static final String RESOURCE = "milvus";

    private final AiCallHealthTracker tracker;
    private final EmbeddingStore<?> embeddingStore;

    public MilvusHealthIndicator(AiCallHealthTracker tracker, EmbeddingStore<?> embeddingStore) {
        this.tracker = tracker;
        this.embeddingStore = embeddingStore;
    }

    @Override
    public Health health() {
        AiCallHealthTracker.HealthStats stats = tracker.getStats(RESOURCE);

        Health.Builder builder = stats.healthy() ? Health.up() : Health.down();
        builder.withDetail("success", stats.success())
                .withDetail("failure", stats.failure())
                .withDetail("total", stats.total())
                .withDetail("errorRate", String.format("%.2f%%", stats.errorRate() * 100))
                .withDetail("embeddingStoreType", embeddingStore.getClass().getSimpleName())
                .withDetail("note", "passive tracking (no active Milvus ping)");
        return builder.build();
    }
}
