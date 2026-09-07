package com.knowledge.ai.ops.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.ops.config.OpsProperties;
import com.knowledge.ai.ops.entity.OpsTrace;
import com.knowledge.ai.ops.mapper.OpsTraceMapper;
import com.knowledge.common.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 链路追踪服务：以 {@link Span}（AutoCloseable）形式埋点，异步落库 ops_trace。
 * <p>
 * 使用方式：
 * <pre>
 *   // 入口（HTTP 请求线程）：开启 ROOT span，自动生成 traceId 并写入 MDC
 *   try (Span root = traceService.startRoot("rag.ask")) {
 *       root.attribute("kbId", kbId);
 *       // 下游：自动作为 child span（无活动链路时返回 noop，零侵入）
 *       try (Span child = traceService.startSpan("milvus.search", SpanType.SEARCH)) {
 *           ...
 *       }
 *   }
 * </pre>
 * <p>设计要点：
 * <ul>
 *   <li><b>采样</b>：{@code ops.trace.sample-rate} 控制采样率，未命中返回 {@link Span#noop()}，
 *       零开销零侵入（调用方无感知）。</li>
 *   <li><b>父子关系</b>：{@link #startRoot} 建立链路 + MDC；{@link #startSpan} 取栈顶为 parent，
 *       无活动链路时返回 noop（避免内部调用产生孤立 trace）。</li>
 *   <li><b>异步落库</b>：复用 {@code aiCallLogExecutor}（TTL 包装），写入失败仅 warn（同 AiCallLogger 范式）。</li>
 *   <li><b>身份捕获</b>：在调用线程取 {@code TenantContext.getTenantId()}，避免异步线程上下文丢失。</li>
 * </ul>
 * <p>与 {@code @InfraMetric} AOP 并存：AOP 计指标（高频写 infra_metric），Trace 计链路（按需写 ops_trace）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
public class TraceService {

    private final OpsTraceMapper mapper;
    private final Executor aiCallLogExecutor;
    private final OpsProperties opsProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TraceService(OpsTraceMapper mapper,
                        @Qualifier("aiCallLogExecutor") Executor aiCallLogExecutor,
                        OpsProperties opsProperties) {
        this.mapper = mapper;
        this.aiCallLogExecutor = aiCallLogExecutor;
        this.opsProperties = opsProperties;
    }

    /**
     * 开启 ROOT span：建立新链路（生成 traceId + 写 MDC）。
     * <p>仅在请求入口调用（如 RagServiceImpl.ask），下游用 {@link #startSpan} 继承链路。
     * <p>采样未命中 / 总开关关闭时返回 {@link Span#noop()}，调用方无感知。
     *
     * @param name span 名（如 rag.ask / agent.run）
     * @return ROOT span（try-with-resources 收尾）
     */
    public Span startRoot(String name) {
        if (!enabled()) {
            return Span.noop();
        }
        if (!sampled()) {
            return Span.noop();
        }
        String traceId = newTraceId();
        String spanId = newTraceId();
        TraceContext.startRoot(traceId);
        TraceContext.pushSpan(spanId);
        return new Span(this, traceId, spanId, null, name, SpanType.ROOT, currentTenantId(), true);
    }

    /**
     * 开启子 span：继承当前链路，parent = 栈顶 spanId。
     * <p>无活动链路时返回 {@link Span#noop()}（避免内部调用产生孤立 trace）。
     *
     * @param name span 名（如 milvus.search / llm.chat）
     * @param type span 类型
     * @return 子 span（无活动链路时为 noop）
     */
    public Span startSpan(String name, SpanType type) {
        if (!enabled() || !TraceContext.hasActiveTrace()) {
            return Span.noop();
        }
        String spanId = newTraceId();
        String parentSpanId = TraceContext.currentSpanId();
        TraceContext.pushSpan(spanId);
        return new Span(this, TraceContext.getTraceId(), spanId, parentSpanId,
                name, type, currentTenantId(), false);
    }

    /** 当前是否启用追踪 */
    private boolean enabled() {
        return opsProperties.getTrace().isEnabled();
    }

    /** 采样判定：[0,1) < sampleRate 则采样 */
    private boolean sampled() {
        double rate = opsProperties.getTrace().getSampleRate();
        return rate >= 1.0 || Math.random() < rate;
    }

    /** 生成 32 位无横线 UUID 作为 traceId/spanId */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static Long currentTenantId() {
        Long id = TenantContext.getTenantId();
        return id != null ? id : TenantContext.PLATFORM_TENANT_ID;
    }

    /** Span 收尾时回调：构建实体 + 异步落库（包级，仅 {@link Span} 调用） */
    void persistSpan(Span span, String status, int durationMs) {
        OpsTrace entity = new OpsTrace();
        entity.setTenantId(span.tenantId() != null ? span.tenantId() : TenantContext.PLATFORM_TENANT_ID);
        entity.setTraceId(span.traceId());
        entity.setSpanId(span.spanId());
        entity.setParentSpanId(span.parentSpanId());
        entity.setSpanName(span.name());
        entity.setSpanType(span.type() == null ? null : span.type().name());
        entity.setStartTime(LocalDateTime.now().minusNanos(durationMs * 1_000_000L));
        entity.setDurationMs(durationMs);
        entity.setStatus(status);
        entity.setAttributesJson(toJson(span.attributes()));
        try {
            aiCallLogExecutor.execute(() -> {
                try {
                    mapper.insert(entity);
                } catch (Exception e) {
                    log.warn("[OpsTrace] 写入失败 trace={} span={} err={}",
                            entity.getTraceId(), entity.getSpanName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("[OpsTrace] 提交异步任务失败: {}", e.getMessage());
        }
    }

    private String toJson(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (Exception e) {
            return null;
        }
    }
}
