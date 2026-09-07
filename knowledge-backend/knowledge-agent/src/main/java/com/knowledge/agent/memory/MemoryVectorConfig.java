package com.knowledge.agent.memory;

import com.knowledge.ai.config.AiProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 记忆向量库 + 异步任务池装配。
 * <p>
 * 设计原因：
 * <ul>
 *   <li>{@code memoryEmbeddingStore}：独立 Milvus 集合（{@code agent_memory_vec}），与文档库
 *       {@code knowledge_chunks} 物理隔离，避免 Agent 长期记忆污染 RAG 检索召回；
 *       连接参数复用 {@link AiProperties.Milvus}（同一 Milvus 实例不同集合），仅 collectionName 取
 *       {@link MemoryProperties}。注入方用 {@code @Qualifier("memoryEmbeddingStore")} 限定。</li>
 *   <li>{@code memoryTaskPool}：摘要生成 / 长期事实抽取的异步执行池，daemon 固定线程池，
 *       与 WorkflowExecutor/AgentExecutor 池隔离，独立命名便于监控。手动线程池 + TenantContext 显式传播，
 *       不依赖 {@code @EnableAsync}，对齐既有异步模式。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MemoryVectorConfig {

    private final AiProperties aiProperties;
    private final MemoryProperties memoryProperties;

    /**
     * 记忆向量存储（独立集合 agent_memory_vec）。
     * <p>注意：不使用 @Primary，仅通过 {@code @Qualifier("memoryEmbeddingStore")} 注入到 VectorMemory；
     * 文档库 bean 由 {@code AiConfig.embeddingStore} 标注 @Primary 保留无限定注入默认。
     */
    @Bean("memoryEmbeddingStore")
    public EmbeddingStore<TextSegment> memoryEmbeddingStore() {
        AiProperties.Milvus c = aiProperties.getMilvus();
        MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder()
                .collectionName(memoryProperties.getCollectionName())
                .dimension(c.getDimension());
        if (c.getUri() != null && !c.getUri().isBlank()) {
            builder.uri(c.getUri());
        } else {
            builder.host(c.getHost()).port(c.getPort());
        }
        log.info("[Memory:Vec] 初始化记忆向量集合 collection={} dim={}",
                memoryProperties.getCollectionName(), c.getDimension());
        return builder.build();
    }

    /**
     * 记忆异步任务线程池（摘要/事实抽取用）。
     * <p>daemon 线程，JVM 退出时自动结束；destroyMethod=shutdown 优雅关闭。
     */
    @Bean(name = "memoryTaskPool", destroyMethod = "shutdown")
    public ExecutorService memoryTaskPool() {
        int size = Math.max(1, memoryProperties.getTaskPoolSize());
        return Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, "memory-task");
            t.setDaemon(true);
            return t;
        });
    }
}
