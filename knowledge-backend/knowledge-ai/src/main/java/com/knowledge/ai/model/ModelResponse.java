package com.knowledge.ai.model;

import java.math.BigDecimal;

/**
 * 模型调用统一响应。
 * <p>跨供应商的响应抽象，携带文本输出、token 明细、费用与耗时，供路由层与调用方审计。
 *
 * @param text             生成文本
 * @param promptTokens     输入 token 数
 * @param completionTokens 输出 token 数
 * @param totalTokens      总 token 数
 * @param model            实际模型名（响应回填）
 * @param providerType     命中的供应商类型
 * @param cost             本次调用估算费用（元）
 * @param durationMs       耗时（毫秒）
 *
 * @author: lxcechoo@gmail.com
 */
public record ModelResponse(
        String text,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String model,
        ProviderType providerType,
        BigDecimal cost,
        long durationMs
) {

    /** 构造空响应（降级 / 熔断场景使用） */
    public static ModelResponse empty(ProviderType providerType) {
        return new ModelResponse("", 0, 0, 0, null, providerType, BigDecimal.ZERO, 0L);
    }
}
