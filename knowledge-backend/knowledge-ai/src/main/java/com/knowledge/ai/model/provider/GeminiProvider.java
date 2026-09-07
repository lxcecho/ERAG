package com.knowledge.ai.model.provider;

import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.config.ModelRouterProperties;

/**
 * Google Gemini 供应商：走官方 OpenAI 兼容端点 {@code /v1beta/openai}，无需额外 SDK。
 *
 * @author: lxcechoo@gmail.com
 */
public class GeminiProvider extends AbstractOpenAiProvider {

    public GeminiProvider(ModelRouterProperties.ProviderConfig config) {
        super(ProviderType.GEMINI, config);
    }
}
