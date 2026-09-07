package com.knowledge.common.alert;

/**
 * 异常告警服务
 * <p>统一收口系统告警能力：结构化 {@code [ALERT]} 日志 + 可选 Webhook 推送。
 * <p>使用场景：
 * <ul>
 *   <li>Sentinel 熔断触发时（CRITICAL）</li>
 *   <li>外部服务（LLM/Milvus/ES）连续失败时（WARN/CRITICAL）</li>
 *   <li>健康检查探测到组件异常时（WARN）</li>
 *   <li>关键业务异常（如 Agent 执行失败）时（WARN）</li>
 * </ul>
 * <p>特性：
 * <ul>
 *   <li>冷却去重：同 {@code source+title} 维度在 cooldownSeconds 内只告警一次，避免风暴</li>
 *   <li>级别过滤：低于 {@link AlertProperties#getMinLevel()} 的告警直接忽略</li>
 *   <li>异步推送：Webhook 推送走独立线程池，失败不影响主流程</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
public interface AlertService {

    /**
     * 发送告警。
     *
     * @param level  告警级别
     * @param source 告警来源（如 "llm:chat" / "milvus:search" / "health:llm"）
     * @param title  告警标题（简短描述，如 "熔断触发" / "连接失败"）
     * @param detail 告警详情（可空，附加上下文信息）
     * @param t      关联异常（可空，传入时打印堆栈）
     */
    void alert(AlertLevel level, String source, String title, String detail, Throwable t);
}
