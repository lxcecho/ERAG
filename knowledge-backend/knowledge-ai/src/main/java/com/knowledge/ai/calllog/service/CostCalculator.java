package com.knowledge.ai.calllog.service;

import com.knowledge.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 模型调用费用计算器。
 * <p>按 {@link AiProperties.Pricing} 配置的单价（元/1K tokens）估算：
 * {@code cost = promptTokens/1000*inputPrice + completionTokens/1000*outputPrice}。
 * <p>未配置的模型走 default 单价；Embedding 类调用通常无 completion，输出单价为 0。
 *
 * @author: lxcechoo@gmail.com
 */
@Component
@RequiredArgsConstructor
public class CostCalculator {

    private final AiProperties aiProperties;

    /**
     * 计算单次调用费用。
     *
     * @param modelName        模型名
     * @param promptTokens     输入 token
     * @param completionTokens 输出 token
     * @return 费用（元，保留 6 位小数）
     */
    public BigDecimal calculate(String modelName, int promptTokens, int completionTokens) {
        AiProperties.Pricing pricing = aiProperties.getPricing();
        double inputPrice = pricing.getDefaultInput();
        double outputPrice = pricing.getDefaultOutput();
        if (modelName != null) {
            AiProperties.Pricing.ModelPrice mp = pricing.getModels().get(modelName);
            if (mp != null) {
                inputPrice = mp.getInput();
                outputPrice = mp.getOutput();
            }
        }
        double cost = promptTokens / 1000.0 * inputPrice + completionTokens / 1000.0 * outputPrice;
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }
}
