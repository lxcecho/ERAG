package com.knowledge.kb.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.mq.config.ParseTaskMqProperties;
import com.knowledge.common.mq.dto.ParseTaskMessage;
import com.knowledge.common.mq.producer.ParseTaskProducer;
import com.knowledge.kb.constant.KbConstants;
import com.knowledge.kb.dto.PageQuery;
import com.knowledge.kb.dto.ParseTaskVo;
import com.knowledge.kb.dto.TaskDetailVo;
import com.knowledge.kb.dto.TaskQuery;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.entity.KbParseTask;
import com.knowledge.kb.event.DocumentParseTaskEvent;
import com.knowledge.kb.mapper.KbParseTaskMapper;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbParseTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 解析任务服务实现。
 * <p>双路径派发：{@code kb.parse.mq.enabled=true} 走 RabbitMQ（ParseTaskProducer 投递，ai 模块消费者异步处理，
 * 支持消息重试/死信/幂等/补偿）；{@code false} 降级走原 {@code @Async} + ApplicationEvent 路径。
 * <p>状态机方法（markProcessingIfPending/markSuccess/markFailed）从 RagEventListener 迁入，
 * 供 MQ 消费者与降级 listener 复用，保证状态流转一致。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class KbParseTaskServiceImpl extends ServiceImpl<KbParseTaskMapper, KbParseTask>
        implements KbParseTaskService {

    private final ApplicationEventPublisher eventPublisher;
    private final KbDocumentService kbDocumentService;
    private final ParseTaskMqProperties mqProps;

    /** MQ 生产者：enabled=false 时不装配，此处允许为 null（降级走事件路径） */
    @Autowired(required = false)
    private ParseTaskProducer parseTaskProducer;

    /**
     * 手动构造器：{@code kbDocumentService} 参数加 {@code @Lazy} 打破与 KbDocumentServiceImpl 的循环依赖。
     * <p>循环链：KbDocumentServiceImpl → KbParseTaskService → KbDocumentService。
     * 因 {@code @Transactional} 触发 AOP 代理，字段级 {@code @Lazy}（Lombok 构造器不传递）不足以打破循环，
     * 须在构造器参数上显式标注 {@code @Lazy}。
     */
    public KbParseTaskServiceImpl(ApplicationEventPublisher eventPublisher,
                                  @Lazy KbDocumentService kbDocumentService,
                                  ParseTaskMqProperties mqProps) {
        this.eventPublisher = eventPublisher;
        this.kbDocumentService = kbDocumentService;
        this.mqProps = mqProps;
    }

    @Override
    public KbParseTask createTask(Long documentId, Long kbId, Long userId) {
        KbParseTask task = new KbParseTask();
        task.setDocumentId(documentId);
        task.setKbId(kbId);
        // 显式设置 tenantId：createTask 在 upload 事务内调用，此时 TenantContext 已由 JWT 设置，
        // 确保任务 tenantId 与上传者/文档一致，避免 MQ 消费者用错误 tenantId 恢复上下文导致向量隔离不匹配
        Long tid = TenantContext.getTenantId();
        if (tid != null) {
            task.setTenantId(tid);
        }
        task.setStatus(KbConstants.TASK_STATUS_PENDING);
        task.setRetryCount(0);
        task.setMaxRetries(mqProps.getMaxRetries());
        task.setErrorMsg("");
        task.setCreatorId(userId);
        save(task);
        return task;
    }

    /**
     * 异步派发解析任务。
     * <p>MQ 模式：投递到 RabbitMQ 主交换机，由 ai 模块 {@code ParseTaskConsumer} 消费（支持重试/死信/幂等）。
     * <p>降级模式：{@code @Async} 发布 {@link DocumentParseTaskEvent}，由 {@code RagEventListener} 同步消费。
     * <p>保留 {@code @Async}：MQ 模式 send 本身轻量但仍异步以免阻塞上传线程；降级模式靠 @Async 异步执行 listener。
     */
    @Async("docAsyncExecutor")
    @Override
    public void process(Long taskId) {
        KbParseTask task = getById(taskId);
        if (task == null) {
            log.warn("[解析任务] task={} 不存在", taskId);
            return;
        }
        if (mqProps.isEnabled() && parseTaskProducer != null) {
            // MQ 模式：投递消息（携带 tenantId 供异步消费恢复租户上下文）
            try {
                int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
                // 确保 tenantId 正确：任务 tenantId 为空/0 时从文档获取（防御式，兼容历史数据）
                Long msgTid = task.getTenantId();
                if (msgTid == null || msgTid == 0L) {
                    KbDocument doc = kbDocumentService.getById(task.getDocumentId());
                    if (doc != null && doc.getTenantId() != null) {
                        msgTid = doc.getTenantId();
                    }
                }
                parseTaskProducer.send(new ParseTaskMessage(
                        task.getId(), task.getDocumentId(), task.getKbId(), msgTid, retry));
                log.info("[解析任务] task={} document={} 已投递 MQ", taskId, task.getDocumentId());
            } catch (Exception e) {
                // MQ 不可用时降级走事件路径，保证任务不丢
                log.error("[解析任务] task={} MQ投递失败，降级走事件路径: {}", taskId, e.getMessage());
                eventPublisher.publishEvent(new DocumentParseTaskEvent(taskId, task.getDocumentId(), task.getKbId()));
            }
        } else {
            // 降级：@Async 事件路径
            log.info("[解析任务] task={} document={} 派发至 RAG 流程（降级事件模式）", taskId, task.getDocumentId());
            eventPublisher.publishEvent(new DocumentParseTaskEvent(taskId, task.getDocumentId(), task.getKbId()));
        }
    }

    @Override
    public IPage<ParseTaskVo> page(Long kbId, Integer pageNo, Integer pageSize) {
        PageQuery query = new PageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return baseMapper.selectTaskPage(query.toPage(), kbId);
    }

    @Override
    public IPage<ParseTaskVo> page(TaskQuery query) {
        return baseMapper.selectTaskPageQuery(query.toPage(), query);
    }

    @Override
    public TaskDetailVo getDetail(Long taskId) {
        TaskDetailVo detail = baseMapper.selectDetail(taskId);
        if (detail == null) {
            throw new BizException("任务不存在");
        }
        return detail;
    }

    /**
     * 重试失败任务：仅失败状态可重试；重置状态/错误信息/重试次数后重新派发。
     * <p>手动重试重置 retry_count=0（给予完整重试预算，区别于补偿任务的自动恢复不重置）。
     */
    @Override
    public void retry(Long taskId) {
        KbParseTask task = getById(taskId);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        if (task.getStatus() != KbConstants.TASK_STATUS_FAILED) {
            throw new BizException("仅失败任务可重试");
        }
        // 重置任务为待处理并同步文档状态为待解析（避免"任务待处理但文档解析失败"的不一致）
        resetToPending(taskId);
        // 手动重试给予完整重试预算：重置 retry_count=0 + 清空错误信息（区别于补偿任务的自动恢复保留累计）
        task = getById(taskId);
        task.setRetryCount(0);
        task.setErrorMsg("");
        updateById(task);
        log.info("[解析任务重试] task={} document={}", taskId, task.getDocumentId());
        // 重新派发（MQ 或降级）
        process(taskId);
    }

    /**
     * 重置任务为待处理状态并同步文档状态为待解析（保留 retry_count 和 error_msg）。
     * <p>供手动重试、补偿任务恢复复用，保证任务与文档状态一致。
     * <p>注意：retry_count 和 error_msg 由调用方按需重置（retry 重置为 0/空，补偿任务保留累计）。
     */
    @Override
    public void resetToPending(Long taskId) {
        KbParseTask task = getById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(KbConstants.TASK_STATUS_PENDING);
        task.setStartTime(null);
        task.setEndTime(null);
        updateById(task);
        // 同步文档状态为待解析，保证任务与文档状态一致
        updateDocStatus(task.getDocumentId(), KbConstants.DOC_STATUS_PENDING, null);
    }

    /* ==================== 状态机方法（供 MQ 消费者与降级 listener 复用） ==================== */

    /**
     * 幂等闸门：条件更新 PENDING/FAILED→PROCESSING 并 retry_count+1。
     * <p>用条件 UPDATE（WHERE status IN(PENDING,FAILED)）保证原子抢占：影响行数=0 即已被处理/并发，
     * 消费端应 ack 丢弃。成功抢占后同步更新文档状态为 PARSING。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markProcessingIfPending(Long taskId) {
        boolean updated = lambdaUpdate()
                .set(KbParseTask::getStatus, KbConstants.TASK_STATUS_PROCESSING)
                .set(KbParseTask::getStartTime, LocalDateTime.now())
                .setSql("retry_count = retry_count + 1")
                .eq(KbParseTask::getId, taskId)
                .in(KbParseTask::getStatus, KbConstants.TASK_STATUS_PENDING, KbConstants.TASK_STATUS_FAILED)
                .update();
        if (updated) {
            KbParseTask task = getById(taskId);
            if (task != null) {
                updateDocStatus(task.getDocumentId(), KbConstants.DOC_STATUS_PARSING, null);
            }
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(Long taskId, int chunkCount) {
        KbParseTask task = getById(taskId);
        if (task != null) {
            task.setStatus(KbConstants.TASK_STATUS_SUCCESS);
            task.setEndTime(LocalDateTime.now());
            updateById(task);
            updateDocStatus(task.getDocumentId(), KbConstants.DOC_STATUS_PARSED, chunkCount);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long taskId, String errorMsg) {
        KbParseTask task = getById(taskId);
        if (task != null) {
            task.setStatus(KbConstants.TASK_STATUS_FAILED);
            task.setErrorMsg(truncate(errorMsg));
            task.setEndTime(LocalDateTime.now());
            updateById(task);
            updateDocStatus(task.getDocumentId(), KbConstants.DOC_STATUS_FAILED, null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailedExhausted(Long taskId, String errorMsg) {
        KbParseTask task = getById(taskId);
        if (task != null) {
            task.setStatus(KbConstants.TASK_STATUS_FAILED);
            task.setErrorMsg(truncate(errorMsg));
            task.setEndTime(LocalDateTime.now());
            // 置为 maxRetries：标记重试耗尽，补偿任务不再自动重投（手动 retry 会重置为 0）
            int max = task.getMaxRetries() != null ? task.getMaxRetries() : mqProps.getMaxRetries();
            task.setRetryCount(max);
            updateById(task);
            updateDocStatus(task.getDocumentId(), KbConstants.DOC_STATUS_FAILED, null);
        }
    }

    /**
     * 更新文档解析状态（chunkCount 非 null 时一并更新）。
     * <p>更新失败（影响行数=0，可能因租户上下文不匹配或文档已被删除）时抛异常，
     * 触发外层 {@code @Transactional} 回滚，保证 task 与 doc 状态原子一致，
     * 避免"任务成功但文档状态滞后"的不一致。
     */
    private void updateDocStatus(Long documentId, int docStatus, Integer chunkCount) {
        KbDocument doc = kbDocumentService.getById(documentId);
        if (doc != null) {
            doc.setStatus(docStatus);
            if (chunkCount != null) {
                doc.setChunkCount(chunkCount);
            }
            if (!kbDocumentService.updateById(doc)) {
                throw new BizException("文档状态更新失败：docId=" + documentId
                        + " docStatus=" + docStatus + "（影响行数0，可能租户上下文不匹配或文档已被删除）");
            }
        }
    }

    private String truncate(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
