package com.knowledge.kb.job;

import com.knowledge.common.alert.AlertLevel;
import com.knowledge.common.alert.AlertService;
import com.knowledge.common.config.RedisCacheService;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.mq.config.ParseTaskMqProperties;
import com.knowledge.kb.constant.KbConstants;
import com.knowledge.kb.entity.KbParseTask;
import com.knowledge.kb.mapper.KbParseTaskMapper;
import com.knowledge.kb.service.KbParseTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档解析任务补偿定时任务。
 * <p>定期扫描两类异常任务并自动恢复，保证「文档解析失败自动恢复」闭环：
 * <ol>
 *   <li><b>卡住任务</b>（PROCESSING 且 start_time 早于阈值）：消费进程崩溃/重启/消息丢失遗留。
 *       <ul>
 *         <li>retry_count &lt; maxRetries：重置为 PENDING 并重新派发（自动恢复）</li>
 *         <li>retry_count &gt;= maxRetries：标记重试耗尽失败 + CRITICAL 告警（避免无限重投）</li>
 *       </ul>
 *   </li>
 *   <li><b>可恢复失败任务</b>（FAILED 且 retry_count &lt; maxRetries）：降级路径失败或未耗尽重试预算的任务。
 *       <ul><li>重置为 PENDING 并重新派发</li></ul>
 *   </li>
 * </ol>
 * <p><b>有界保证</b>：每次重投经 {@code markProcessingIfPending} 使 retry_count+1，达 maxRetries 后不再自动恢复，
 * 需人工调用 {@code retry()}（重置 retry_count=0）重新给予完整预算。
 * <p><b>租户上下文</b>：扫描跨租户（Mapper @InterceptorIgnore），重投前按任务 tenantId 设置上下文，
 * process() 的 @Async 经 TTL 传播至异步消费线程。
 * <p><b>双模式兼容</b>：MQ 模式重投走 ParseTaskProducer；降级模式（mq.enabled=false）走 @Async 事件，
 * 均由 {@link KbParseTaskService#process} 统一分发。
 * <p>需 @EnableScheduling（已加在 KnowledgeApplication）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParseTaskCompensationJob {

    private final KbParseTaskMapper parseTaskMapper;
    private final KbParseTaskService kbParseTaskService;
    private final ParseTaskMqProperties props;
    private final AlertService alertService;
    private final RedisCacheService redisCacheService;

    /**
     * 补偿扫描主入口：默认每 5 分钟执行（cron 可配置）。
     */
    @Scheduled(cron = "${kb.parse.mq.compensation.cron:0 */5 * * * ?}")
    public void compensate() {
        if (!props.getCompensation().isEnabled()) {
            return;
        }
        int maxRetries = props.getMaxRetries();
        log.info("[补偿-定时] 开始扫描异常解析任务（maxRetries={}）", maxRetries);
        int recovered = 0;
        int exhausted = 0;
        try {
            recovered += recoverStuckTasks(maxRetries);
            recovered += recoverFailedTasks(maxRetries);
            exhausted += markStuckExhausted(maxRetries);
            if (recovered > 0 || exhausted > 0) {
                log.info("[补偿-定时] 扫描完成：重投 {} 个，标记耗尽 {} 个", recovered, exhausted);
            }
        } catch (Exception e) {
            log.error("[补偿-定时] 扫描异常", e);
        }
    }

    /**
     * 恢复卡住任务（PROCESSING 超时且有重试预算）：重置 PENDING + 重新派发。
     *
     * @return 本轮重投的任务数
     */
    private int recoverStuckTasks(int maxRetries) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(props.getCompensation().getStuckThresholdMinutes());
        List<KbParseTask> stuckTasks = parseTaskMapper.selectStuckTasks(KbConstants.TASK_STATUS_PROCESSING, threshold);
        if (stuckTasks.isEmpty()) {
            return 0;
        }
        log.warn("[补偿-定时] 发现 {} 个卡住任务（PROCESSING 超过 {} 分钟）", stuckTasks.size(),
                props.getCompensation().getStuckThresholdMinutes());
        int recovered = 0;
        for (KbParseTask task : stuckTasks) {
            if (task.getRetryCount() != null && task.getRetryCount() >= maxRetries) {
                // 预算耗尽的卡住任务交给 markStuckExhausted 统一处理
                continue;
            }
            String lockKey = "parse:doc:" + task.getDocumentId();
            if (!redisCacheService.tryLock(lockKey, 300)) {
                log.info("[补偿-定时] 未获取锁，跳过卡住任务 task={}", task.getId());
                continue;
            }
            TenantContext.setTenantId(task.getTenantId());
            try {
                // 重置任务为待处理并同步文档状态（避免"任务待处理但文档解析中"的不一致）
                kbParseTaskService.resetToPending(task.getId());
                kbParseTaskService.process(task.getId());
                recovered++;
                log.info("[补偿-定时] 卡住任务重投 task={} doc={} retry={}/{}",
                        task.getId(), task.getDocumentId(), task.getRetryCount(), maxRetries);
                alertService.alert(AlertLevel.WARN, "parse:compensation:stuck",
                        "卡住解析任务自动恢复",
                        "taskId=" + task.getId() + " documentId=" + task.getDocumentId()
                                + " retry=" + task.getRetryCount() + "/" + maxRetries,
                        null);
            } finally {
                TenantContext.clear();
                redisCacheService.unlock(lockKey);
            }
        }
        return recovered;
    }

    /**
     * 恢复可恢复的失败任务（FAILED 且 retry_count < maxRetries）：重置 PENDING + 重新派发。
     * <p>覆盖降级路径（@Async 事件）失败的任务；MQ 路径失败的任务经 DLQ 标记 retry_count=maxRetries，不会被扫描到。
     *
     * @return 本轮重投的任务数
     */
    private int recoverFailedTasks(int maxRetries) {
        List<KbParseTask> failedTasks = parseTaskMapper.selectRecoverableFailedTasks(
                KbConstants.TASK_STATUS_FAILED, maxRetries);
        if (failedTasks.isEmpty()) {
            return 0;
        }
        log.info("[补偿-定时] 发现 {} 个可恢复失败任务", failedTasks.size());
        int recovered = 0;
        for (KbParseTask task : failedTasks) {
            String lockKey = "parse:doc:" + task.getDocumentId();
            if (!redisCacheService.tryLock(lockKey, 300)) {
                log.info("[补偿-定时] 未获取锁，跳过失败任务 task={}", task.getId());
                continue;
            }
            TenantContext.setTenantId(task.getTenantId());
            try {
                // 重置任务为待处理并同步文档状态（避免"任务待处理但文档解析失败"的不一致）
                kbParseTaskService.resetToPending(task.getId());
                kbParseTaskService.process(task.getId());
                recovered++;
                log.info("[补偿-定时] 失败任务重投 task={} doc={} retry={}/{}",
                        task.getId(), task.getDocumentId(), task.getRetryCount(), maxRetries);
            } finally {
                TenantContext.clear();
                redisCacheService.unlock(lockKey);
            }
        }
        return recovered;
    }

    /**
     * 标记卡住且重试预算耗尽的任务为失败（retry_count=maxRetries）+ CRITICAL 告警。
     * <p>避免持久故障的卡住任务被无限重投；需人工 retry() 重置预算后才能恢复。
     *
     * @return 本轮标记耗尽的任务数
     */
    private int markStuckExhausted(int maxRetries) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(props.getCompensation().getStuckThresholdMinutes());
        List<KbParseTask> stuckTasks = parseTaskMapper.selectStuckTasks(KbConstants.TASK_STATUS_PROCESSING, threshold);
        int exhausted = 0;
        for (KbParseTask task : stuckTasks) {
            if (task.getRetryCount() == null || task.getRetryCount() < maxRetries) {
                continue;
            }
            TenantContext.setTenantId(task.getTenantId());
            try {
                kbParseTaskService.markFailedExhausted(task.getId(),
                        "任务卡住且重试预算耗尽（PROCESSING 超时 " + props.getCompensation().getStuckThresholdMinutes()
                                + " 分钟，retry=" + task.getRetryCount() + "），需人工介入");
                exhausted++;
                log.error("[补偿-定时] 卡住任务标记耗尽 task={} doc={} retry={}/{}",
                        task.getId(), task.getDocumentId(), task.getRetryCount(), maxRetries);
                alertService.alert(AlertLevel.CRITICAL, "parse:compensation:exhausted",
                        "卡住解析任务重试预算耗尽",
                        "taskId=" + task.getId() + " documentId=" + task.getDocumentId()
                                + " kbId=" + task.getKbId() + " retry=" + task.getRetryCount() + "/" + maxRetries,
                        null);
            } finally {
                TenantContext.clear();
            }
        }
        return exhausted;
    }
}
