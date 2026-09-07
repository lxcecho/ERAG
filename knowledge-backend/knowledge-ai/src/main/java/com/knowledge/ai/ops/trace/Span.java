package com.knowledge.ai.ops.trace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 链路追踪 span：{@link AutoCloseable} 实现，支持 try-with-resources 自动收尾。
 * <p>
 * 使用方式：
 * <pre>
 *   try (Span span = traceService.startSpan("milvus.search", SpanType.SEARCH)) {
 *       span.attribute("kbId", kbId).attribute("topK", topK);
 *       List&lt;RetrievalResult&gt; results = milvusService.search(...);
 *       span.success();
 *       return results;
 *   } catch (RuntimeException e) {
 *       // span 已在 catch 中标记 error，close() 幂等
 *       throw e;
 *   }
 * </pre>
 * <p>语义：
 * <ul>
 *   <li>{@link #success()}：标记 OK 并落库（后续 close 幂等）</li>
 *   <li>{@link #error(Throwable)}：标记 ERROR + 错误属性并落库</li>
 *   <li>{@link #close()}：未明确收尾时视为成功（兜底）</li>
 * </ul>
 * <p>{@link #noop()} 返回单例空 span，禁用追踪 / 采样未命中 / 无活动链路时使用，
 * 调用方无需 null 判断。
 *
 * @author: lxcechoo@gmail.com
 */
public class Span implements AutoCloseable {

    /** 空操作单例（禁用追踪 / 采样未命中 / 无父链路时返回） */
    private static final Span NOOP = new Span();

    /** 获取空操作 span（方法均无副作用） */
    public static Span noop() {
        return NOOP;
    }

    /* ==================== 真实 span 字段（NOOP 时全为 null/默认） ==================== */

    private final TraceService service;
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final SpanType type;
    private final Long tenantId;
    private final boolean root;
    private final long startMs;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private volatile boolean finished = false;

    /** NOOP 构造（全部字段置空，方法无副作用） */
    private Span() {
        this.service = null;
        this.traceId = null;
        this.spanId = null;
        this.parentSpanId = null;
        this.name = null;
        this.type = null;
        this.tenantId = null;
        this.root = false;
        this.startMs = 0L;
    }

    /** 真实 span 构造（包级，仅 {@link TraceService} 调用） */
    Span(TraceService service, String traceId, String spanId, String parentSpanId,
         String name, SpanType type, Long tenantId, boolean root) {
        this.service = service;
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.type = type;
        this.tenantId = tenantId;
        this.root = root;
        this.startMs = System.currentTimeMillis();
    }

    /** 追加属性（kbId/topK/tokenUsage 等），链式调用；NOOP / 已收尾时忽略 */
    public Span attribute(String key, Object value) {
        if (service != null && !finished && key != null) {
            attributes.put(key, value);
        }
        return this;
    }

    /** 标记成功并落库 */
    public void success() {
        finish("OK", null);
    }

    /** 标记失败并落库（错误信息写入 attributes.error） */
    public void error(Throwable e) {
        finish("ERROR", e == null ? null : e.getMessage());
    }

    /** try-with-resources 兜底：未明确收尾视为成功 */
    @Override
    public void close() {
        finish("OK", null);
    }

    /** 收尾：计算耗时 + 落库 + 弹栈（幂等） */
    private void finish(String status, String errorMsg) {
        if (service == null || finished) {
            return;
        }
        finished = true;
        int durationMs = (int) (System.currentTimeMillis() - startMs);
        if (errorMsg != null) {
            attributes.put("error", truncate(errorMsg, 1024));
        }
        service.persistSpan(this, status, durationMs);
        TraceContext.popSpan(spanId);
        if (root) {
            TraceContext.clear();
        }
    }

    /* ==================== 落库读取字段（TraceService 序列化用） ==================== */

    String traceId() {
        return traceId;
    }

    String spanId() {
        return spanId;
    }

    String parentSpanId() {
        return parentSpanId;
    }

    String name() {
        return name;
    }

    SpanType type() {
        return type;
    }

    Long tenantId() {
        return tenantId;
    }

    Map<String, Object> attributes() {
        return attributes;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
