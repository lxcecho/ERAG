/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.ops.trace;

import com.knowledge.ai.ops.config.OpsProperties;
import com.knowledge.ai.ops.entity.OpsTrace;
import com.knowledge.ai.ops.mapper.OpsTraceMapper;
import com.knowledge.common.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 链路追踪服务单测。
 * <p>风格：plain JUnit5 + Mockito mock()，SUT 用 new 构造传 mock。
 * <p>异步落库验证：注入同步 Executor（{@code Runnable::run}），使 insert 立即执行。
 * <p>覆盖：ROOT span 建链 + MDC、父子 span 继承、close 兜底、ERROR 标记、noop 降级、MDC 清理。
 * <p>注意：{@link Span} 的 spanId()/parentSpanId()/traceId() 为包级可见，同包测试可直接断言父子关系。
 */
class TraceServiceTest {

    private OpsTraceMapper mapper;
    private OpsProperties opsProperties;
    private TraceService traceService;

    @BeforeEach
    void setUp() {
        mapper = mock(OpsTraceMapper.class);
        opsProperties = new OpsProperties();
        opsProperties.getTrace().setEnabled(true);
        opsProperties.getTrace().setSampleRate(1.0);
        // 同步 Executor：insert 立即执行，便于断言
        traceService = new TraceService(mapper, Runnable::run, opsProperties);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        MDC.clear();
    }

    private OpsTrace captureSingle() {
        ArgumentCaptor<OpsTrace> captor = ArgumentCaptor.forClass(OpsTrace.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    // ==================== ROOT span 建链 + MDC ====================

    @Test
    void startRoot_建立链路_写入MDC并落库ROOT() {
        assertNull(MDC.get("traceId"), "建链前 MDC 无 traceId");
        try (Span root = traceService.startRoot("rag.ask")) {
            root.attribute("kbId", 10);
            // 链路活动期间 MDC 应写入 traceId
            assertNotNull(MDC.get("traceId"), "链路活动期间 MDC 有 traceId");
            root.success();
        }
        OpsTrace entity = captureSingle();
        assertEquals("rag.ask", entity.getSpanName());
        assertEquals(SpanType.ROOT.name(), entity.getSpanType());
        assertEquals("OK", entity.getStatus());
        assertNull(entity.getParentSpanId(), "ROOT span 无 parent");
        assertEquals(1L, entity.getTenantId());
        assertNotNull(entity.getTraceId());
        assertNotNull(entity.getSpanId());
        assertTrue(entity.getAttributesJson().contains("\"kbId\":10"));
        // ROOT 关闭后 MDC 应清理（防线程池复用串链路）
        assertNull(MDC.get("traceId"), "ROOT 关闭后 MDC 清理");
    }

    @Test
    void startRoot_close兜底_视为OK() {
        try (Span root = traceService.startRoot("rag.ask")) {
            // 不调用 success/error，直接 close 兜底
        }
        OpsTrace entity = captureSingle();
        assertEquals("OK", entity.getStatus());
    }

    // ==================== 父子 span 继承 ====================

    @Test
    void startSpan_继承父链_parentSpanId为栈顶span() {
        Span root = traceService.startRoot("rag.ask");
        Span child = traceService.startSpan("milvus.search", SpanType.SEARCH);

        // 父子关系：child.parentSpanId == root.spanId，traceId 一致
        assertEquals(root.spanId(), child.parentSpanId(), "child 的 parent 即 root");
        assertEquals(root.traceId(), child.traceId(), "同链路 traceId 一致");

        child.success();
        child.close();
        root.success();
        root.close();

        // child 先关闭（先落库），root 后关闭（后落库）
        ArgumentCaptor<OpsTrace> captor = ArgumentCaptor.forClass(OpsTrace.class);
        verify(mapper, times(2)).insert(captor.capture());
        OpsTrace childEntity = captor.getAllValues().get(0);
        OpsTrace rootEntity = captor.getAllValues().get(1);

        assertEquals(childEntity.getParentSpanId(), rootEntity.getSpanId(), "落库 parent 指向 root spanId");
        assertEquals(childEntity.getTraceId(), rootEntity.getTraceId(), "落库 traceId 一致");
        assertEquals(SpanType.SEARCH.name(), childEntity.getSpanType());
        assertEquals(SpanType.ROOT.name(), rootEntity.getSpanType());
    }

    // ==================== ERROR 标记 ====================

    @Test
    void error_标记ERROR_错误信息写入attributes() {
        try (Span root = traceService.startRoot("rag.ask")) {
            root.error(new RuntimeException("milvus timeout"));
        }
        OpsTrace entity = captureSingle();
        assertEquals("ERROR", entity.getStatus());
        assertNotNull(entity.getAttributesJson());
        assertTrue(entity.getAttributesJson().contains("\"error\""), "attributes 含 error 键");
        assertTrue(entity.getAttributesJson().contains("milvus timeout"), "attributes 含错误信息");
    }

    @Test
    void success后close_幂等仅落库一次() {
        Span root = traceService.startRoot("rag.ask");
        root.success();
        root.close();
        verify(mapper, times(1)).insert(any(OpsTrace.class));
    }

    // ==================== noop 降级 ====================

    @Test
    void traceDisabled_返回noop_不落库不写MDC() {
        opsProperties.getTrace().setEnabled(false);
        try (Span root = traceService.startRoot("rag.ask")) {
            assertSame(Span.noop(), root, "禁用时返回 noop 单例");
            root.success();
        }
        verifyNoInteractions(mapper);
        assertNull(MDC.get("traceId"), "禁用时不写 MDC");
    }

    @Test
    void startSpan_无活动链路_返回noop() {
        // 未 startRoot，无活动链路 → startSpan 返回 noop
        Span noop = traceService.startSpan("milvus.search", SpanType.SEARCH);
        assertSame(Span.noop(), noop);
        noop.success();
        noop.close();
        verifyNoInteractions(mapper);
    }

    @Test
    void noop_span属性方法无副作用() {
        Span noop = Span.noop();
        // noop 上调用任意方法均不应落库
        noop.attribute("k", "v").success();
        noop.error(new RuntimeException("x"));
        noop.close();
        verifyNoInteractions(mapper);
    }
}
