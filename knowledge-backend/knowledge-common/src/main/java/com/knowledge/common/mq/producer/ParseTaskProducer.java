package com.knowledge.common.mq.producer;

import com.knowledge.common.mq.config.ParseTaskMqProperties;
import com.knowledge.common.mq.dto.ParseTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 文档解析任务消息生产者。
 * <p>仅在 {@code kb.parse.mq.enabled=true} 时装配；false 时降级由 {@code KbParseTaskServiceImpl}
 * 直接发 {@code DocumentParseTaskEvent}（@Async 事件路径），本 bean 不存在。
 * <p>降级时 kb 侧用 {@code @Autowired(required=false)} 注入，null 判空走事件路径。
 * <p>消息持久化：RabbitTemplate 默认 MessageDeliveryMode.PERSISTENT，重启不丢任务。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kb.parse.mq.enabled", havingValue = "true")
public class ParseTaskProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ParseTaskMqProperties props;

    /**
     * 投递解析任务消息到主交换机。
     *
     * @param msg 解析任务消息（携带 taskId 作为幂等键 + tenantId 用于异步消费恢复租户上下文）
     */
    public void send(ParseTaskMessage msg) {
        rabbitTemplate.convertAndSend(props.getExchange(), props.getRoutingKey(), msg);
        log.info("[MQ生产] 投递解析任务 task={} doc={} kb={} retry={}",
                msg.taskId(), msg.documentId(), msg.kbId(), msg.retryCount());
    }
}
