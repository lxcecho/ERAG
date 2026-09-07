package com.knowledge.common.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 队列拓扑配置（文档解析任务）。
 * <p>仅在 {@code kb.parse.mq.enabled=true} 时装配；false 时降级走 @Async 事件，无 MQ 依赖。
 * <p>拓扑：
 * <pre>
 *   Exchange: kb.parse.task (direct)
 *     └─ Queue: kb.parse.task  ──(消费)──► ParseTaskConsumer
 *         x-dead-letter-exchange=kb.parse.task.dlx
 *         x-dead-letter-routing-key=dead
 *   Exchange: kb.parse.task.dlx (direct)
 *     └─ Queue: kb.parse.task.dlq  ──(消费)──► ParseTaskConsumer.handleDlq
 * </pre>
 * <p><b>重试 + 死信机制（模式C）</b>：消费失败由 Spring AMQP RetryTemplate 重试
 * （{@code spring.rabbitmq.listener.simple.retry.*}，3 次 backoff 10s/30s/90s）；
 * 重试耗尽后 {@code default-requeue-rejected=false} 使消息 nack 不回原队列，
 * 由主队列的 {@code x-dead-letter-exchange} 自动路由到 DLQ。无需 RepublishMessageRecoverer。
 *
 * @author: lxcechoo@gmail.com
 */
@Configuration
@ConditionalOnProperty(name = "kb.parse.mq.enabled", havingValue = "true")
public class RabbitMqConfig {

    /** 主交换机（投递解析任务） */
    @Bean
    public DirectExchange parseTaskExchange(ParseTaskMqProperties props) {
        return new DirectExchange(props.getExchange(), true, false);
    }

    /** 主队列：消费端监听；声明死信路由到 DLX */
    @Bean
    public Queue parseTaskQueue(ParseTaskMqProperties props) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", props.getDlxExchange());
        args.put("x-dead-letter-routing-key", props.getDlqRoutingKey());
        return QueueBuilder.durable(props.getQueue()).withArguments(args).build();
    }

    /** 主队列绑定主交换机 */
    @Bean
    public Binding parseTaskBinding(Queue parseTaskQueue, DirectExchange parseTaskExchange,
                                    ParseTaskMqProperties props) {
        return BindingBuilder.bind(parseTaskQueue).to(parseTaskExchange).with(props.getRoutingKey());
    }

    /** 死信交换机（重试耗尽消息路由目标） */
    @Bean
    public DirectExchange parseTaskDlx(ParseTaskMqProperties props) {
        return new DirectExchange(props.getDlxExchange(), true, false);
    }

    /** 死信队列：重试耗尽归宿 */
    @Bean
    public Queue parseTaskDlq(ParseTaskMqProperties props) {
        return QueueBuilder.durable(props.getDlqQueue()).build();
    }

    /** 死信队列绑定死信交换机 */
    @Bean
    public Binding parseTaskDlqBinding(Queue parseTaskDlq, DirectExchange parseTaskDlx,
                                       ParseTaskMqProperties props) {
        return BindingBuilder.bind(parseTaskDlq).to(parseTaskDlx).with(props.getDlqRoutingKey());
    }

    /** JSON 消息转换器：ParseTaskMessage 序列化/反序列化 */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** RabbitTemplate：使用 JSON 转换器，由生产者注入使用 */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter rabbitMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter);
        return template;
    }
}
