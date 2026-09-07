package com.knowledge.common.alert;

/**
 * 告警级别枚举
 * <p>用于 {@link AlertService#alert} 标识告警严重程度，决定日志级别与是否触发 Webhook 推送。
 * <ul>
 *   <li>{@link #INFO}：信息级告警，仅记录日志（低于默认 min-level 时忽略）</li>
 *   <li>{@link #WARN}：警告级，部分依赖异常、限流触发</li>
 *   <li>{@link #CRITICAL}：严重级，熔断触发、核心服务不可用，必推送 Webhook</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
public enum AlertLevel {

    /** 信息级（默认不告警，需将 min-level 设为 INFO 才生效） */
    INFO,

    /** 警告级（默认最低告警级别） */
    WARN,

    /** 严重级（熔断/核心服务宕机，必推送） */
    CRITICAL;

    /**
     * 级别比较：当前级别是否 >= 给定级别。
     *
     * @param other 比较基准
     * @return 当前级别不低于基准时返回 true
     */
    public boolean atLeast(AlertLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
