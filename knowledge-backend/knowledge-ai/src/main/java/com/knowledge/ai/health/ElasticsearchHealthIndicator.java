package com.knowledge.ai.health;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Elasticsearch 健康检查
 * <p><b>主动探测</b>：通过底层 {@link RestClient} 调用 {@code GET /_cluster/health} 轻量接口（不查业务索引）。
 * <p>{@code @ConditionalOnProperty(name="es.enabled", havingValue="true")}：仅 ES 启用时装配，
 * dev 环境未配 ES 时不注册此指标，避免拖垮整体健康状态。
 * <p>异常处理：连接超时/拒绝 → DOWN（附异常信息），HTTP 非 2xx → DEGRADED。
 *
 * @author: lxcechoo@gmail.com
 */
@Component("elasticsearchHealthIndicator")
@ConditionalOnProperty(name = "es.enabled", havingValue = "true")
public class ElasticsearchHealthIndicator implements HealthIndicator {

    /** ES 集群健康探测端点（轻量，不扫描索引） */
    private static final String HEALTH_ENDPOINT = "/_cluster/health";

    private final RestClient restClient;

    public ElasticsearchHealthIndicator(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Health health() {
        try {
            Response response = restClient.performRequest(new Request("GET", HEALTH_ENDPOINT));
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return Health.up()
                        .withDetail("endpoint", HEALTH_ENDPOINT)
                        .withDetail("statusCode", statusCode)
                        .build();
            }
            return Health.down()
                    .withDetail("endpoint", HEALTH_ENDPOINT)
                    .withDetail("statusCode", statusCode)
                    .withDetail("error", "non-2xx response")
                    .build();
        } catch (IOException e) {
            return Health.down()
                    .withDetail("endpoint", HEALTH_ENDPOINT)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
