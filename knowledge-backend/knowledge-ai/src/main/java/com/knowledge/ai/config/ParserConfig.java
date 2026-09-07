package com.knowledge.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 解析器配置
 */
@Configuration
public class ParserConfig {

    /**
     * MinerU服务RestTemplate
     */
    @Bean("minerURestTemplate")
    @ConditionalOnProperty(name = "ai.parser.type", havingValue = "mineru")
    public RestTemplate minerURestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(180))  // PDF解析可能较慢
                .build();
    }
}
