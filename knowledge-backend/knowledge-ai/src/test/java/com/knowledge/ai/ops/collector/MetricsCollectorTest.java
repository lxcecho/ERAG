/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.ops.collector;

import com.knowledge.ai.ops.entity.InfraMetric;
import com.knowledge.ai.ops.mapper.InfraMetricMapper;
import com.knowledge.common.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 基础设施指标采集器单测。
 * <p>风格：plain JUnit5 + Mockito mock()，SUT 用 new 构造传 mock。
 * <p>异步落库验证：注入同步 Executor（{@code Runnable::run}），使 insert 在调用线程立即执行，便于断言。
 * <p>注意：{@link MetricsCollector.MetricTracer} 有 {@code close()} 但未实现 {@link AutoCloseable}，
 * 故测试用显式 {@code close()} 调用（与生产代码埋点范式一致），不用 try-with-resources。
 * <p>覆盖：成功/失败标记、close 兜底成功、幂等收尾、MQ 延迟计算、metadata 序列化、租户身份捕获。
 */
class MetricsCollectorTest {

    private InfraMetricMapper mapper;
    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        mapper = mock(InfraMetricMapper.class);
        // 同步 Executor：execute 直接运行，避免异步等待，便于即时断言 insert
        collector = new MetricsCollector(mapper, Runnable::run);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 捕获落库的 InfraMetric 实体（同步 Executor 保证 insert 已完成） */
    private InfraMetric captureSingle() {
        ArgumentCaptor<InfraMetric> captor = ArgumentCaptor.forClass(InfraMetric.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    // ==================== Tracer 成功/失败/兜底 ====================

    @Test
    void success_标记成功_落库success为1且无错误信息() {
        MetricsCollector.MetricTracer tracer = collector.start("milvus:search", "search");
        tracer.success();
        tracer.close();
        InfraMetric m = captureSingle();
        assertEquals("milvus:search", m.getResource());
        assertEquals("search", m.getAction());
        assertEquals(1, m.getSuccess());
        assertNull(m.getErrorMsg());
        assertEquals(1L, m.getTenantId());
        assertTrue(m.getDurationMs() >= 0, "耗时非负");
    }

    @Test
    void failure_标记失败_落库success为0且携带错误信息() {
        MetricsCollector.MetricTracer tracer = collector.start("es:search", "search");
        tracer.failure(new RuntimeException("connection refused"));
        tracer.close();
        InfraMetric m = captureSingle();
        assertEquals("es:search", m.getResource());
        assertEquals(0, m.getSuccess());
        assertEquals("connection refused", m.getErrorMsg());
    }

    @Test
    void close_未明确收尾_兜底视为成功() {
        // 不调用 success/failure，直接 close 兜底视为成功
        MetricsCollector.MetricTracer tracer = collector.start("milvus:search", "search");
        tracer.close();
        InfraMetric m = captureSingle();
        assertEquals(1, m.getSuccess());
        assertNull(m.getErrorMsg());
    }

    @Test
    void success后close_幂等仅落库一次() {
        MetricsCollector.MetricTracer tracer = collector.start("milvus:search", "search");
        tracer.success();
        tracer.close(); // 幂等，不应再次落库
        verify(mapper, times(1)).insert(any(InfraMetric.class));
    }

    @Test
    void failure后close_幂等仅落库一次() {
        MetricsCollector.MetricTracer tracer = collector.start("milvus:search", "search");
        tracer.failure(new RuntimeException("err"));
        tracer.close(); // 幂等
        verify(mapper, times(1)).insert(any(InfraMetric.class));
    }

    // ==================== MQ 延迟专用 ====================

    @Test
    void recordMqLatency_给定enqueueTime_计算延迟落库() {
        long enqueue = System.currentTimeMillis() - 500; // 500ms 前投递
        collector.recordMqLatency("mq:parse", enqueue, true, null);
        InfraMetric m = captureSingle();
        assertEquals("mq:parse", m.getResource());
        assertEquals("consume", m.getAction());
        assertEquals(1, m.getSuccess());
        // 延迟应接近 500ms（允许调度抖动，下限 400ms）
        assertTrue(m.getDurationMs() >= 400, "MQ 延迟应接近 500ms，实际=" + m.getDurationMs());
    }

    @Test
    void recordMqLatency_enqueueTime为null_延迟兜底为0() {
        collector.recordMqLatency("mq:parse", null, false, "consume error");
        InfraMetric m = captureSingle();
        assertEquals(0, m.getDurationMs());
        assertEquals(0, m.getSuccess());
        assertEquals("consume error", m.getErrorMsg());
    }

    // ==================== metadata 序列化 ====================

    @Test
    void start_携带metadata_序列化为JSON() {
        MetricsCollector.MetricTracer tracer =
                collector.start("milvus:search", "search", Map.of("kbId", 10, "topK", 5));
        tracer.success();
        tracer.close();
        InfraMetric m = captureSingle();
        assertNotNull(m.getMetadata());
        assertTrue(m.getMetadata().contains("\"kbId\":10"), "metadata 含 kbId");
        assertTrue(m.getMetadata().contains("\"topK\":5"), "metadata 含 topK");
    }

    @Test
    void start_无metadata_字段为null() {
        MetricsCollector.MetricTracer tracer = collector.start("milvus:search", "search");
        tracer.success();
        tracer.close();
        InfraMetric m = captureSingle();
        assertNull(m.getMetadata());
    }

    // ==================== 租户身份捕获 ====================

    @Test
    void tenantId_启动时捕获_上下文清理后仍正确() {
        MetricsCollector.MetricTracer tracer = collector.start("milvus:search", "search");
        TenantContext.clear(); // 模拟异步线程上下文丢失
        tracer.success();
        InfraMetric m = captureSingle();
        // tenantId 在 start 时捕获，不受后续 clear 影响
        assertEquals(1L, m.getTenantId());
    }
}
