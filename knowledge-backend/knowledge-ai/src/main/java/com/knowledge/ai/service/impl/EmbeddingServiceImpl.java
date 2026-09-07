package com.knowledge.ai.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.knowledge.ai.calllog.service.AiCallLogger;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.health.AiCallHealthTracker;
import com.knowledge.ai.service.EmbeddingService;
import com.knowledge.common.alert.AlertLevel;
import com.knowledge.common.alert.AlertService;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.result.ResultCode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 服务实现：委托 LangChain4j {@link EmbeddingModel}。
 * <p>每次调用通过 {@link AiCallLogger} 埋点，记录耗时/状态至 ai_call_log。
 * Embedding 供应商通常不返回 token usage（输出为向量而非文本），且 LangChain4j 的
 * {@code Response} metadata 在不同版本 API 不稳定，故 token 记 0；费用按 Embedding 单价（output=0）估算为 0，
 * 调用本身仍被审计。如需精确 token，可后续接入分词器估算输入长度。
 * <p><b>生产能力</b>：查询向量化路径 {@link #embed} 加 {@link SentinelResource}（资源名 {@code embedding:embed}），
 * 连续失败触发熔断后由 blockHandler 抛 {@link BizException}（经全局异常处理器返回 4291 降级码）。
 * embedAll（文档入库批量向量化）为异步 best-effort 路径，不加熔断（失败由上游任务重试兜底）。
 * 调用成功/失败上报 {@link AiCallHealthTracker}（"embedding" 资源，供未来健康检查扩展）。
 *
 * @author: lxcechoo@gmail.com
 */
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final String RESOURCE = "embedding:embed";

    /**
     * 批量向量化分批大小：从配置读取（ai.embedding.batch-size），默认 64。
     * <p>不同供应商上限不同：dashscope ≤25，OpenAI ≤2048，Ollama 无硬限制。
     * 较原硬编码 10 大幅提升文档入库吞吐（约 6x），同时分批摊薄单请求耗时避免超时。
     */
    private int batchSize() {
        return aiProperties.getEmbedding().getBatchSize();
    }

    private final EmbeddingModel embeddingModel;
    private final AiProperties aiProperties;
    private final AiCallLogger aiCallLogger;
    private final AiCallHealthTracker healthTracker;
    private final AlertService alertService;

    @Override
    @SentinelResource(value = RESOURCE, blockHandler = "embedBlockHandler")
    public Embedding embed(String text) {
        AiCallLogger.Tracer tracer = aiCallLogger.trace("embedding", "EMBEDDING", modelName());
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            tracer.success(0, 0);
            healthTracker.recordSuccess("embedding");
            return embedding;
        } catch (RuntimeException e) {
            tracer.failure(e);
            healthTracker.recordFailure("embedding");
            throw e;
        }
    }

    @Override
    public List<Embedding> embedAll(List<TextSegment> segments) {
        AiCallLogger.Tracer tracer = aiCallLogger.trace("embedding", "EMBEDDING", modelName());
        try {
            // 分批向量化：避免大文档一次请求全部切片导致超时（单批超时仍由 embedding.timeout-seconds 兜底）
            List<Embedding> embeddings = new ArrayList<>(segments.size());
            int bs = batchSize();
            for (int i = 0; i < segments.size(); i += bs) {
                List<TextSegment> batch = segments.subList(i, Math.min(i + bs, segments.size()));
                embeddings.addAll(embeddingModel.embedAll(batch).content());
            }
            tracer.success(0, 0);
            healthTracker.recordSuccess("embedding");
            return embeddings;
        } catch (RuntimeException e) {
            tracer.failure(e);
            healthTracker.recordFailure("embedding");
            throw e;
        }
    }

    /**
     * 查询向量化降级：熔断/限流时抛 {@link BizException}，经全局异常处理器返回 4291 降级码。
     * <p>查询向量化是 RAG 检索的前置依赖，无法返回降级值（无向量则无法召回），故抛异常让上层返回清晰错误。
     */
    public Embedding embedBlockHandler(String text, BlockException ex) {
        alertService.alert(AlertLevel.CRITICAL, RESOURCE, "Embedding调用降级",
                "blockType=" + ex.getClass().getSimpleName(), ex);
        throw new BizException(ResultCode.SERVICE_DEGRADED);
    }

    private String modelName() {
        return aiProperties.getEmbedding().getModelName();
    }
}
