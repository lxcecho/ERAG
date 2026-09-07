package com.knowledge.common.mq.dto;

/**
 * 文档解析任务消息体（RabbitMQ 消息载荷）。
 * <p>跨模块共用：knowledge-kb 生产（{@code KbParseTaskServiceImpl.process}）→ MQ → knowledge-ai 消费
 * （{@code ParseTaskConsumer}）。故定义在 common 模块，避免 kb/ai 互相依赖。
 * <p>{@code tenantId} 显式携带：MQ 消费线程无 SecurityContext，消费时需手动设置 TenantContext
 * （复用项目「异步线程显式身份」约定）。
 * <p>{@code enqueueTime} 用于 MQ 延迟指标（运维指标 #6）：生产者投递时记 epoch millis，
 * 消费时延迟 = now - enqueueTime。向后兼容：旧消息反序列化无此字段时为 null，兜底 now（0 延迟）。
 *
 * @param taskId     解析任务ID（幂等键）
 * @param documentId 文档ID
 * @param kbId       知识库ID
 * @param tenantId   所属租户ID（异步消费线程无 SecurityContext，显式传递）
 * @param retryCount 当前重试次数（首次投递为 0）
 * @param enqueueTime 投递时间（epoch millis，运维 MQ 延迟指标用）
 *
 * @author: lxcechoo@gmail.com
 */
public record ParseTaskMessage(Long taskId, Long documentId, Long kbId, Long tenantId, int retryCount,
                               Long enqueueTime) {

    /** 向后兼容构造（旧调用方无 enqueueTime，投递时补 now） */
    public ParseTaskMessage(Long taskId, Long documentId, Long kbId, Long tenantId, int retryCount) {
        this(taskId, documentId, kbId, tenantId, retryCount, System.currentTimeMillis());
    }
}
