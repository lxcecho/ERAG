package com.knowledge.ai.model.strategy;

import com.knowledge.ai.model.ModelContext;
import com.knowledge.ai.model.ModelProvider;
import com.knowledge.ai.model.ModelRequest;
import com.knowledge.ai.model.ModelStrategy;
import com.knowledge.ai.model.RouteStrategyType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按模型名称路由：匹配 {@link ModelContext#preferredModel()}（或 {@link ModelRequest#preferredModel()）指定的模型。
 * <p>匹配规则：先精确匹配 {@link ModelProvider#modelName()}，再包含匹配（如 "gpt-4o" 命中 "gpt-4o-mini"），
 * 均不命中返回 null（Router 回退到默认策略或下一候选）。
 *
 * @author: lxcechoo@gmail.com
 */
@Component
public class NameRouteStrategy implements ModelStrategy {

    @Override
    public RouteStrategyType type() {
        return RouteStrategyType.NAME;
    }

    @Override
    public ModelProvider select(List<ModelProvider> candidates, ModelContext ctx, ModelRequest req) {
        String preferred = ctx.preferredModel();
        if ((preferred == null || preferred.isBlank()) && req != null) {
            preferred = req.preferredModel();
        }
        if (preferred == null || preferred.isBlank()) {
            return null;
        }
        // 1. 精确匹配
        for (ModelProvider p : candidates) {
            if (preferred.equalsIgnoreCase(p.modelName())) {
                return p;
            }
        }
        // 2. 包含匹配
        String lower = preferred.toLowerCase();
        for (ModelProvider p : candidates) {
            if (p.modelName() != null && p.modelName().toLowerCase().contains(lower)) {
                return p;
            }
        }
        return null;
    }
}
