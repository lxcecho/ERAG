package com.knowledge.ai.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Elasticsearch 客户端装配：手动构建 RestClient + ElasticsearchClient。
 * <p>设计原因：手动建 Bean 使 Spring Boot 的自动配置（{@code RestClientAutoConfiguration} /
 * {@code ElasticsearchClientAutoConfiguration}）因 {@code @ConditionalOnMissingBean} 退让，
 * 便于精细控制超时、认证与传输层，不依赖 spring.elasticsearch.* 自动配置项。
 *
 * @author: lxcechoo@gmail.com
 */
@Configuration
@EnableConfigurationProperties(EsProperties.class)
public class EsConfig {

    /**
     * 底层 RestClient：承载 HTTP 连接池与认证。
     * <p>destroyMethod="close" 保证容器关闭时释放连接池。
     */
    @Bean(destroyMethod = "close")
    public RestClient esRestClient(EsProperties props) {
        URI uri = URI.create(props.getUris());
        RestClientBuilder builder = RestClient.builder(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()));

        // 基本认证（开启 xpack 安全时）
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            BasicCredentialsProvider creds = new BasicCredentialsProvider();
            creds.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(props.getUsername(), props.getPassword()));
            builder.setHttpClientConfigCallback(hc -> hc.setDefaultCredentialsProvider(creds));
        }

        // 超时
        builder.setRequestConfigCallback(rc -> rc
                .setConnectTimeout((int) props.getConnectTimeout().toMillis())
                .setSocketTimeout((int) props.getSocketTimeout().toMillis()));

        return builder.build();
    }

    /**
     * 高层 ElasticsearchClient：基于 RestClient 传输，业务层只依赖此接口。
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient esRestClient) {
        return new ElasticsearchClient(new RestClientTransport(esRestClient, new JacksonJsonpMapper()));
    }
}
