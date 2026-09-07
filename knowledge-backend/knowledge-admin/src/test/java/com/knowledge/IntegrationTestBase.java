package com.knowledge;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类：使用 Testcontainers 启动 MySQL 容器。
 * <p>所有集成测试继承此类，自动获得隔离的 MySQL 实例，无需依赖本地数据库。
 * <p>使用方式：
 * <pre>
 * class MyServiceTest extends IntegrationTestBase {
 *     @Autowired
 *     private MyService myService;
 *
 *     @Test
 *     void testSomething() {
 *         // 测试代码
 *     }
 * }
 * </pre>
 *
 * @author: lxcechoo@gmail.com
 */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.33")
            .withDatabaseName("knowledge_ai_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/init-test.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        // 禁用不需要的外部依赖
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "16379");
        registry.add("ai.milvus.host", () -> "localhost");
        registry.add("ai.milvus.port", () -> "19530");
        registry.add("spring.elasticsearch.uris", () -> "localhost:9200");
        registry.add("spring.rabbitmq.host", () -> "localhost");

        // 禁用需要外部依赖的功能
        registry.add("ai.rag.semantic-cache.enabled", () -> "false");
        registry.add("ai.rag.hybrid.enabled", () -> "false");
        registry.add("kb.parse.mq.enabled", () -> "false");
        registry.add("kb.governance.enabled", () -> "false");
        registry.add("alert.enabled", () -> "false");
    }
}
