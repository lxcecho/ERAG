package com.knowledge.ai.ops.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.ops.entity.InfraMetric;
import com.knowledge.ai.ops.mapper.InfraMetricMapper;
import com.knowledge.common.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 基础设施指标采集器：以 Tracer 风格埋点 Milvus/ES/MQ 调用，异步落库 infra_metric。
 * <p>使用方式：
 * <pre>
 *   MetricsCollector.MetricTracer tracer = collector.start("milvus:search", "search");
 *   try {
 *       List<RetrievalResult> results = milvusService.search(...);
 *       tracer.success();
 *   } catch (Exception e) {
 *       tracer.failure(e);
 *       throw e;
 *   }
 * </pre>
 * <p>MQ 延迟专用 {@link #recordMqLatency}：直接给定 enqueueTime，计算延迟 = now - enqueueTime。
 * <p>异步落库：复用 {@code aiCallLogExecutor}（TTL 包装），写入失败仅 warn 不抛出（同 AiCallLogger 范式）。
 * <p>身份捕获：在调用线程取 {@code TenantContext.getTenantId()}，避免异步线程上下文丢失。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
public class MetricsCollector {

    private final InfraMetricMapper mapper;
    private final Executor aiCallLogExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetricsCollector(InfraMetricMapper mapper,
                            @Qualifier("aiCallLogExecutor") Executor aiCallLogExecutor) {
        this.mapper = mapper;
        this.aiCallLogExecutor = aiCallLogExecutor;
    }

    /** 开始一个指标 Tracer（自动计时），无附加元数据 */
    public MetricTracer start(String resource, String action) {
        return start(resource, action, null);
    }

    /** 开始一个指标 Tracer，携带附加元数据（kbId/topK 等） */
    public MetricTracer start(String resource, String action, Map<String, Object> metadata) {
        return new MetricTracer(this, resource, action, metadata, TenantContext.getTenantId());
    }

    /**
     * MQ 延迟专用：直接给定 enqueueTime（epoch millis），计算延迟 = now - enqueueTime。
     * <p>跨进程时间戳，AOP 无法表达，故 MQ 场景用手工埋点。
     *
     * @param resource    资源名（如 mq:parse）
     * @param enqueueTime 投递时间（epoch millis，null 时兜底 now 视为 0 延迟）
     * @param success     是否成功
     * @param errorMsg    失败原因（可空）
     */
    public void recordMqLatency(String resource, Long enqueueTime, boolean success, String errorMsg) {
        long now = System.currentTimeMillis();
        long enqueue = enqueueTime != null ? enqueueTime : now;
        int durationMs = (int) Math.max(0, now - enqueue);
        InfraMetric metric = newMetric(resource, "consume", durationMs, success, errorMsg, null);
        persist(metric);
    }

    /** 异步写入（程序式异步，避免 @Async 自调用代理失效） */
    void persist(InfraMetric metric) {
        try {
            aiCallLogExecutor.execute(() -> {
                try {
                    mapper.insert(metric);
                } catch (Exception e) {
                    log.warn("[InfraMetric] 写入失败 resource={} err={}",
                            metric.getResource(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("[InfraMetric] 提交异步任务失败: {}", e.getMessage());
        }
    }

    /** 构建 InfraMetric 实体（填充 tenantId） */
    private InfraMetric newMetric(String resource, String action, int durationMs,
                                  boolean success, String errorMsg, Map<String, Object> metadata) {
        InfraMetric m = new InfraMetric();
        m.setTenantId(Objects.requireNonNullElse(TenantContext.getTenantId(), TenantContext.PLATFORM_TENANT_ID));
        m.setResource(resource);
        m.setAction(action);
        m.setDurationMs(durationMs);
        m.setSuccess(success ? 1 : 0);
        m.setErrorMsg(truncate(errorMsg, 512));
        m.setMetadata(toJson(metadata));
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 指标追踪器：捕获起始时刻 + 租户身份，在 success/failure 时计算耗时并异步落库。
     * <p>支持 try-with-resources：未明确调用 success/failure 而直接 close 时视为成功。
     */
    public static class MetricTracer {
        private final MetricsCollector collector;
        private final String resource;
        private final String action;
        private final Map<String, Object> metadata;
        private final Long tenantId;
        private final long startMs;
        private volatile boolean finished = false;

        MetricTracer(MetricsCollector collector, String resource, String action,
                     Map<String, Object> metadata, Long tenantId) {
            this.collector = collector;
            this.resource = resource;
            this.action = action;
            this.metadata = metadata;
            this.tenantId = tenantId;
            this.startMs = System.currentTimeMillis();
        }

        /** 标记成功并落库 */
        public void success() {
            finish(true, null);
        }

        /** 标记失败并落库 */
        public void failure(Throwable e) {
            finish(false, e != null ? e.getMessage() : null);
        }

        /** try-with-resources 兜底：未明确成功失败视为成功 */
        public void close() {
            if (!finished) {
                finish(true, null);
            }
        }

        private void finish(boolean success, String errorMsg) {
            if (finished) {
                return;
            }
            finished = true;
            int durationMs = (int) (System.currentTimeMillis() - startMs);
            InfraMetric m = new InfraMetric();
            m.setTenantId(Objects.requireNonNullElse(tenantId, TenantContext.PLATFORM_TENANT_ID));
            m.setResource(resource);
            m.setAction(action);
            m.setDurationMs(durationMs);
            m.setSuccess(success ? 1 : 0);
            m.setErrorMsg(truncate(errorMsg, 512));
            m.setMetadata(collector.toJson(metadata));
            collector.persist(m);
        }

        private static String truncate(String s, int max) {
            if (s == null) {
                return null;
            }
            return s.length() > max ? s.substring(0, max) : s;
        }
    }
}
