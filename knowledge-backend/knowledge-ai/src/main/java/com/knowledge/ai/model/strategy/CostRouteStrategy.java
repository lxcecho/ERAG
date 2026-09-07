package com.knowledge.ai.model.strategy;

import com.knowledge.ai.model.ModelContext;
import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRequest;
import com.knowledge.ai.model.ModelStrategy;
import com.knowledge.ai.model.RouteStrategyType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 按成本路由：预估本次调用费用，选单价最低的供应商。
 * <p>预估公式：{@code cost = estPrompt/1000*inputPrice + estCompletion/1000*outputPrice}，
 * 其中 estPrompt 按输入字符数 ÷ 4 粗估，estCompletion 按 {@code maxTokens/2} 预估。
 * <p>{@link ModelContext#costBudget()} 非空时，超预算的供应商被排除；全部超预算返回 null。
 *
 * @author: lxcechoo@gmail.com
 */
@Component
public class CostRouteStrategy implements ModelStrategy {

    @Override
    public RouteStrategyType type() {
        return RouteStrategyType.COST;
    }

    @Override
    public ModelProvider select(List<ModelProvider> candidates, ModelContext ctx, ModelRequest req) {
        int estPrompt = estimatePromptTokens(req);
        int estCompletion = estimateCompletionTokens(req);
        BigDecimal budget = ctx.costBudget();

        ModelProvider best = null;
        BigDecimal bestCost = null;
        for (ModelProvider p : candidates) {
            BigDecimal cost = p.inputPrice().multiply(BigDecimal.valueOf(estPrompt))
                    .add(p.outputPrice().multiply(BigDecimal.valueOf(estCompletion)))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            if (budget != null && cost.compareTo(budget) > 0) {
                continue;
            }
            if (bestCost == null || cost.compareTo(bestCost) < 0) {
                bestCost = cost;
                best = p;
            }
        }
        return best;
    }

    private int estimatePromptTokens(ModelRequest req) {
        if (req == null) {
            return 0;
        }
        int chars = 0;
        if (req.userPrompt() != null) {
            chars += req.userPrompt().length();
        }
        if (req.systemPrompt() != null) {
            chars += req.systemPrompt().length();
        }
        return chars / 4 + 1;
    }

    private int estimateCompletionTokens(ModelRequest req) {
        if (req != null && req.maxTokens() != null && req.maxTokens() > 0) {
            return req.maxTokens() / 2;
        }
        return 512;
    }
}
