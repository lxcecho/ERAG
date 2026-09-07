package com.knowledge.ai.ops.trace;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.slf4j.MDC;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 链路追踪上下文：以 {@link TransmittableThreadLocal} 持有 traceId + spanId 栈，
 * 跨线程池传播（同 {@code TenantContext} 范式，@Async 线程池已 TTL 包装）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>traceId</b>：整条链路唯一，ROOT span 创建时生成，写入 MDC("traceId") 便于日志关联。</li>
 *   <li><b>spanId 栈</b>：压栈/弹栈维护父子关系，栈顶 = 当前 span 的 parent。</li>
 *   <li><b>清理</b>：ROOT span 关闭时清理全部上下文 + MDC，防线程池复用串链路与内存泄漏。</li>
 * </ul>
 * <p>包级可见：仅 {@link TraceService} / {@link Span} 内部使用，外部不直接操作。
 *
 * @author: lxcechoo@gmail.com
 */
final class TraceContext {

    private TraceContext() {
    }

    /** traceId 持有器（整条链路一致） */
    private static final TransmittableThreadLocal<String> TRACE_ID = new TransmittableThreadLocal<>();

    /** spanId 栈（栈顶为当前活动 span，其下即父链） */
    private static final TransmittableThreadLocal<Deque<String>> SPAN_STACK = new TransmittableThreadLocal<>();

    /** 启动新链路：设置 traceId + MDC + 空栈 */
    static void startRoot(String traceId) {
        TRACE_ID.set(traceId);
        SPAN_STACK.set(new ArrayDeque<>());
        MDC.put("traceId", traceId);
    }

    /** 当前 traceId（无活动链路返回 null） */
    static String getTraceId() {
        return TRACE_ID.get();
    }

    /** 是否存在活动链路 */
    static boolean hasActiveTrace() {
        return TRACE_ID.get() != null;
    }

    /** 压栈新 spanId */
    static void pushSpan(String spanId) {
        Deque<String> stack = SPAN_STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            SPAN_STACK.set(stack);
        }
        stack.push(spanId);
    }

    /** 弹栈指定 spanId（仅弹栈顶匹配项，避免乱序误弹） */
    static void popSpan(String spanId) {
        Deque<String> stack = SPAN_STACK.get();
        if (stack != null && !stack.isEmpty() && spanId.equals(stack.peek())) {
            stack.pop();
        }
    }

    /** 当前栈顶 spanId（即下一个 child span 的 parent），无活动 span 返回 null */
    static String currentSpanId() {
        Deque<String> stack = SPAN_STACK.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }

    /** 清理整条链路（ROOT span 关闭时调用） */
    static void clear() {
        TRACE_ID.remove();
        SPAN_STACK.remove();
        MDC.remove("traceId");
    }
}
