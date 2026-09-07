package com.knowledge.ai.model;

/**
 * 模型路由策略类型。
 * <p>对应四种路由维度，由 {@link com.knowledge.ai.model.ModelStrategy} 各实现承载，
 * {@link com.knowledge.ai.model.ModelContext#strategy()} 指定本次调用走哪种策略，缺省走默认策略。
 *
 * @author: lxcechoo@gmail.com
 */
public enum RouteStrategyType {

    /** 按模型名称路由：匹配 preferredModel 指定的供应商 */
    NAME,

    /** 按成本路由：选单位 token 价格最低的供应商 */
    COST,

    /** 按 Token 数路由：按请求预估 token 规模选上下文容量匹配且经济的供应商 */
    TOKEN,

    /** 按租户路由：按 tenantId 查租户偏好供应商映射 */
    TENANT
}
