package com.knowledge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j / OpenAPI3）
 * <p>Knife4j 在 Spring Boot 3 下自动装配文档 UI（默认 /doc.html），
 * 此处仅补充文档元信息。具体接口分组、扫描包通过 application.yml 的
 * springdoc.group-configs 配置。
 * 
 * @author: lxcechoo@gmail.com
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("企业级 AI 知识库助手 API")
                        .description("Knowledge AI Assistant 接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("knowledge")
                                .url("https://github.com/")
                                .email("dev@knowledge.com"))
                        .license(new License().name("Apache 2.0")));
    }
}
