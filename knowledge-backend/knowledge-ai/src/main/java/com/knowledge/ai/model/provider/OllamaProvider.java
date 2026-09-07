package com.knowledge.ai.model.provider;

import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.config.ModelRouterProperties;

/**
 * Ollama 本地供应商：官方提供 OpenAI 兼容端点 {@code http://localhost:11434/v1}。
 * <p>Ollama 不校验 API Key，但 {@link #available()} 要求 apiKey 非空，
 * 故配置时填任意占位串（如 {@code ollama}）即可。
 *
 * @author: lxcechoo@gmail.com
 */
public class OllamaProvider extends AbstractOpenAiProvider {

    public OllamaProvider(ModelRouterProperties.ProviderConfig config) {
        super(ProviderType.OLLAMA, config);
    }
}
