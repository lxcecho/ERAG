package com.knowledge.common.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档解析任务 MQ 配置属性
 * <p>对应 application.yml 中 {@code kb.parse.mq.*} 配置项。
 * <p>策略：默认开启 MQ 模式（enabled=true）；设为 false 时降级回退原 {@code @Async} + ApplicationEvent 路径，
 * 消费者与 MQ 配置类不装配，CachingConnectionFactory 默认 lazy 不连接，应用可正常启动（零影响降级）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "kb.parse.mq")
public class ParseTaskMqProperties {

    /** MQ 模式总开关：true=走 RabbitMQ，false=降级 @Async 事件 */
    private boolean enabled = true;

    /** 主交换机名（direct） */
    private String exchange = "kb.parse.task";

    /** 主队列名（消费端监听） */
    private String queue = "kb.parse.task";

    /** 主路由键 */
    private String routingKey = "parse";

    /** 死信交换机名（重试耗尽路由目标） */
    private String dlxExchange = "kb.parse.task.dlx";

    /** 死信队列名 */
    private String dlqQueue = "kb.parse.task.dlq";

    /** 死信路由键 */
    private String dlqRoutingKey = "dead";

    /** 最大重试次数（与 spring.rabbitmq.listener.simple.retry.max-attempts 对齐） */
    private int maxRetries = 3;

    /** 任务补偿配置 */
    private Compensation compensation = new Compensation();

    /**
     * 任务补偿配置：定时扫描卡住/失败任务自动重投。
     */
    @Data
    public static class Compensation {

        /** 补偿任务开关 */
        private boolean enabled = true;

        /** 扫描 cron（默认每 5 分钟） */
        private String cron = "0 */5 * * * ?";

        /** PROCESSING 超过此分钟数视为卡住（进程崩溃/重启遗留） */
        private int stuckThresholdMinutes = 10;
    }
}
