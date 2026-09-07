package com.knowledge.ai.mq.consumer;

import com.knowledge.ai.dto.IngestResult;
import com.knowledge.ai.ops.collector.MetricResource;
import com.knowledge.ai.ops.collector.MetricsCollector;
import com.knowledge.ai.service.RagService;
import com.knowledge.common.alert.AlertLevel;
import com.knowledge.common.alert.AlertService;
import com.knowledge.common.config.RedisCacheService;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.mq.dto.ParseTaskMessage;
import com.knowledge.kb.constant.KbConstants;
import com.knowledge.kb.entity.KbParseTask;
import com.knowledge.kb.service.KbParseTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 文档解析任务 MQ 消费者。
 * <p>仅在 {@code kb.parse.mq.enabled=true} 时装配。监听 RabbitMQ 主队列消费解析任务，
 * 失败由 Spring AMQP RetryTemplate 重试（3 次 backoff），重试耗尽经队列 DLX 路由到 DLQ 由 {@link #handleDlq} 处理。
 * <p><b>幂等设计</b>：
 * <ul>
 *   <li>消费前检查任务状态：已 SUCCESS 直接 ack 丢弃（防重复投递）</li>
 *   <li>首次消费（PENDING/FAILED）用 {@code markProcessingIfPending} 条件更新原子抢占→PROCESSING</li>
 *   <li>RetryTemplate 重试时（PROCESSING）跳过抢占标记，直接重试 ingest（操作级幂等靠 chunkId 确定性覆盖）</li>
 * </ul>
 * <p><b>租户上下文</b>：MQ 消费线程无 SecurityContext，从消息体取 tenantId 显式设置 + finally 清理。
 * <p><b>MQ 延迟指标</b>（运维指标 #6）：消费时记录延迟 = now - msg.enqueueTime，
 * 成功/失败均记录（先记成功，catch 中补记失败），写入 infra_metric。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kb.parse.mq.enabled", havingValue = "true")
public class ParseTaskConsumer {

    private final RagService ragService;
    private final KbParseTaskService kbParseTaskService;
    private final AlertService alertService;
    private final MetricsCollector metricsCollector;
    private final RedisCacheService redisCacheService;

    /**
     * 消费主队列消息：执行文档解析（解析→切片→向量化→Milvus→ES）。
     * <p>失败抛异常由 RetryTemplate 重试；重试耗尽后消息经 DLX 进 DLQ。
     */
    @RabbitListener(queues = "${kb.parse.mq.queue}")
    public void onMessage(ParseTaskMessage msg) {
        Long taskId = msg.taskId();
        String lockKey = "parse:doc:" + msg.documentId();
        log.info("[MQ消费] 收到解析任务 task={} doc={}", taskId, msg.documentId());

        // 分布式锁：同一文档同一时刻只允许一个消费者/补偿任务处理
        if (!redisCacheService.tryLock(lockKey, 600)) {
            log.info("[MQ消费] 未获取锁，跳过 task={}", taskId);
            return;
        }
        try {
            // 1. 幂等检查：任务不存在或已成功 → ack 丢弃（防重复投递）
            KbParseTask task = kbParseTaskService.getById(taskId);
            if (task == null) {
                log.warn("[MQ消费] task={} 不存在，丢弃", taskId);
                return;
            }
            if (task.getStatus() == KbConstants.TASK_STATUS_SUCCESS) {
                log.info("[MQ消费] task={} 已成功，跳过（幂等）", taskId);
                return;
            }

            // 2. 抢占标记：首次消费（PENDING/FAILED）条件更新→PROCESSING；重试时（PROCESSING）跳过直接处理
            if (task.getStatus() == KbConstants.TASK_STATUS_PENDING
                    || task.getStatus() == KbConstants.TASK_STATUS_FAILED) {
                if (!kbParseTaskService.markProcessingIfPending(taskId)) {
                    // 并发抢占失败（已被其他消费者处理），跳过
                    log.info("[MQ消费] task={} 并发抢占失败，跳过", taskId);
                    return;
                }
            }

            // 3. 设置租户上下文（异步线程无 SecurityContext，从消息体恢复）
            TenantContext.setTenantId(msg.tenantId());
            try {
                IngestResult result = ragService.ingest(msg.documentId());
                kbParseTaskService.markSuccess(taskId, result.getChunkCount());
                // MQ 延迟指标（成功）：延迟 = now - enqueueTime
                metricsCollector.recordMqLatency(MetricResource.MQ_PARSE, msg.enqueueTime(), true, null);
                log.info("[MQ消费] 解析完成 task={} doc={} chunks={}", taskId, msg.documentId(), result.getChunkCount());
            } catch (Exception e) {
                // MQ 延迟指标（失败）：仍记录延迟，便于统计失败请求的排队时间
                metricsCollector.recordMqLatency(MetricResource.MQ_PARSE, msg.enqueueTime(), false, e.getMessage());
                // 抛出由 RetryTemplate 重试；重试耗尽后经 DLX 进 DLQ
                log.warn("[MQ消费] 解析失败 task={} doc={} err={}（将由 RetryTemplate 重试）",
                        taskId, msg.documentId(), e.getMessage());
                throw e;
            } finally {
                TenantContext.clear();
            }
        } finally {
            redisCacheService.unlock(lockKey);
        }
    }

    /**
     * 消费死信队列：重试耗尽的任务最终归宿。
     * <p>标记任务重试耗尽失败（retry_count=maxRetries，防补偿任务无限重投）+ 发送 CRITICAL 告警。
     */
    @RabbitListener(queues = "${kb.parse.mq.dlq-queue}")
    public void handleDlq(ParseTaskMessage msg) {
        Long taskId = msg.taskId();
        log.error("[MQ死信] task={} doc={} 解析重试耗尽，进入死信队列", taskId, msg.documentId());
        kbParseTaskService.markFailedExhausted(taskId, "解析重试耗尽（达最大重试次数），需人工介入");
        alertService.alert(AlertLevel.CRITICAL, "parse:dlq",
                "文档解析重试耗尽",
                "taskId=" + taskId + " documentId=" + msg.documentId() + " kbId=" + msg.kbId(),
                null);
    }
}
