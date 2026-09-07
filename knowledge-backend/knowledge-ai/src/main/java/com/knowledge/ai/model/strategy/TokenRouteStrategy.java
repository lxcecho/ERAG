package com.knowledge.ai.model.strategy;

import com.knowledge.ai.model.ModelContext;
import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRequest;
import com.knowledge.ai.model.ModelStrategy;
import com.knowledge.ai.model.RouteStrategyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 按 Token 数路由：按请求预估 token 规模选上下文容量匹配且经济的供应商。
 * <p>
 * 选择逻辑：
 * <ol>
 *   <li>预估本次所需 token = estPrompt + maxTokens（输出预留）</li>
 *   <li>过滤 {@link ModelProvider#maxTokens()} 容量不足者；{@link ModelContext#tokenBudget()} 非空时排除超预算者</li>
 *   <li>容量充足者中选单价最低的（避免大请求浪费昂贵大上下文模型，小请求用便宜模型）</li>
 *   <li>全部容量不足时，选容量最大的兜底（让最有可能容纳的供应商试）</li>
 * </ol>
 *
 * @author: lxcechoo@gmail.com
 */
@Component
public class TokenRouteStrategy implements ModelStrategy {

    @Override
    public RouteStrategyType type() {
        return RouteStrategyType.TOKEN;
    }

    @Override
    public ModelProvider select(List<ModelProvider> candidates, ModelContext ctx, ModelRequest req) {
        int estPrompt = estimatePromptTokens(req);
        int estCompletion = (req != null && req.maxTokens() != null && req.maxTokens() > 0)
                ? req.maxTokens() : 1024;
        int need = estPrompt + estCompletion;
        Integer budget = ctx.tokenBudget();

        List<ModelProvider> feasible = new ArrayList<>();
        for (ModelProvider p : candidates) {
            if (p.maxTokens() < need) {
                continue;
            }
            if (budget != null && need > budget) {
                continue;
            }
            feasible.add(p);
        }
        if (feasible.isEmpty()) {
            // 无供应商容量充足，选容量最大的兜底
            return candidates.stream()
                    .max(Comparator.comparingInt(ModelProvider::maxTokens))
                    .orElse(null);
        }
        // 容量充足中选最经济的
        return feasible.stream()
                .min(Comparator.comparing(p -> p.inputPrice().add(p.outputPrice())))
                .orElse(null);
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
}
