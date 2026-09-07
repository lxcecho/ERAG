package com.knowledge.ai.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * AI 基础设施装配：将 LangChain4j 的模型与向量库封装为 Spring Bean。
 * <p>设计原因：通过 {@link AiProperties} 注入配置，业务层只依赖接口（ChatModel /
 * EmbeddingModel / EmbeddingStore / DocumentSplitter），切换供应商或调参无需改动业务代码。
 *
 * @author: lxcechoo@gmail.com
 */
@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final AiProperties props;

    /**
     * 对话模型：OpenAI 兼容协议，可对接 DeepSeek / 通义千问 / OpenAI。
     */
    @Bean
    public ChatModel chatModel() {
        AiProperties.Llm c = props.getLlm();
        return OpenAiChatModel.builder()
                .baseUrl(c.getBaseUrl())
                .apiKey(c.getApiKey())
                .modelName(c.getModelName())
                .temperature(c.getTemperature())
                .maxTokens(c.getMaxTokens())
                .build();
    }

    /**
     * Embedding 模型：将文本映射为向量，供 Milvus 存储与检索。
     * <p>超时按 {@code embedding.timeout-seconds} 配置（默认 180s）：本地 Ollama CPU 批量向量化大文档较慢，
     * 默认 60s 易触发 request timed out 导致解析任务卡在重试循环。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        AiProperties.Embedding c = props.getEmbedding();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(c.getBaseUrl())
                .apiKey(c.getApiKey())
                .modelName(c.getModelName())
                .timeout(Duration.ofSeconds(c.getTimeoutSeconds()))
                .build();
    }

    /**
     * Milvus 向量存储：自动按配置维度创建集合（若不存在）。
     * <p>标注 {@link Primary}：记忆中心新增 {@code memoryEmbeddingStore}（独立集合 agent_memory_vec）后，
     * 产生两个 {@code EmbeddingStore<TextSegment>} bean，此处 @Primary 保证 MilvusServiceImpl 等
     * 无限定注入仍解析到文档库 bean，记忆库仅由 VectorMemory 通过 @Qualifier 显式注入。
     */
    @Bean
    @Primary
    public EmbeddingStore<TextSegment> embeddingStore() {
        AiProperties.Milvus c = props.getMilvus();
        MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder()
                .collectionName(c.getCollectionName())
                .dimension(c.getDimension());
        if (c.getUri() != null && !c.getUri().isBlank()) {
            builder.uri(c.getUri());
        } else {
            builder.host(c.getHost()).port(c.getPort());
        }
        return builder.build();
    }

    /**
     * 流式对话模型：SSE 流式输出场景使用，逐 token 推送至前端。
     * <p>独立于 {@link #chatModel()} 的同步模型，二者共用同一供应商配置，
     * 仅超时按 stream-timeout 单独设置以适配长连接。
     */
    @Bean
    public StreamingChatModel streamingChatModel() {
        AiProperties.Llm c = props.getLlm();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(c.getBaseUrl())
                .apiKey(c.getApiKey())
                .modelName(c.getModelName())
                .temperature(c.getTemperature())
                .maxTokens(c.getMaxTokens())
                .timeout(Duration.ofSeconds(c.getStreamTimeout()))
                .build();
    }

    /**
     * 文本切片器：递归按字符切分并保留重叠，兼顾上下文连续性。
     */
    @Bean
    public DocumentSplitter documentSplitter() {
        return DocumentSplitters.recursive(props.getRag().getChunkSize(), props.getRag().getChunkOverlap());
    }
}
