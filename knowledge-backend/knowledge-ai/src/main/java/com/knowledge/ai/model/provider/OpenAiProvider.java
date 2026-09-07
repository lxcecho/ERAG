package com.knowledge.ai.model.provider;

import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.config.ModelRouterProperties;

/**
 * OpenAI 供应商：原生 OpenAI 兼容协议。
 *
 * @author: lxcechoo@gmail.com
 */
public class OpenAiProvider extends AbstractOpenAiProvider {

    public OpenAiProvider(ModelRouterProperties.ProviderConfig config) {
        super(ProviderType.OPENAI, config);
    }
}
