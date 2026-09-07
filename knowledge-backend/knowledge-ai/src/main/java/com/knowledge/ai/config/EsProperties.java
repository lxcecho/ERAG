package com.knowledge.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Elasticsearch 连接与索引配置（读取 application.yml 中 es.* 配置）。
 * <p>设计原因：与 ai.milvus 平级，独立管理 ES 连接参数；混合检索的 ES 路通过此配置寻址。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@ConfigurationProperties(prefix = "es")
public class EsProperties {

    /** ES 地址（单节点），如 http://localhost:9200 */
    private String uris = "http://localhost:9200";

    /** 用户名（开启 xpack 安全时配置，本地开发可为空） */
    private String username;

    /** 密码 */
    private String password;

    /** 连接超时 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 读超时 */
    private Duration socketTimeout = Duration.ofSeconds(30);

    /** 索引名（与 Milvus collection 同名对齐，便于理解） */
    private String indexName = "knowledge_chunks";

    /** 是否启用 ES（false 时混合检索自动降级为纯向量） */
    private boolean enabled = true;
}
