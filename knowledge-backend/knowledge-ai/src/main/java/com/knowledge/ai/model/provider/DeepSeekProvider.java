package com.knowledge.ai.model.provider;

import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.config.ModelRouterProperties;

/**
 * DeepSeek 供应商：原生 OpenAI 兼容协议（{@code https://api.deepseek.com/v1}）。
 *
 * @author: lxcechoo@gmail.com
 */
public class DeepSeekProvider extends AbstractOpenAiProvider {

    public DeepSeekProvider(ModelRouterProperties.ProviderConfig config) {
        super(ProviderType.DEEPSEEK, config);
    }
}
