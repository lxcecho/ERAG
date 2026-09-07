package com.knowledge.kb.governance.job;

import com.knowledge.kb.governance.config.GovernanceProperties;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeLifecycleService;
import com.knowledge.kb.governance.service.KnowledgeGovernanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识治理定时任务：扫描过期文档 / 自动归档 / 保留期硬删除。
 * <p>三组任务 cron 可独立配置：
 * <ul>
 *   <li>{@code expire-cron}：扫描 expire_at 过期但仍 APPROVED 的文档，置为 REJECTED + 审计；</li>
 *   <li>{@code auto-archive-cron}：按每 KB 策略 autoArchiveDays 归档超期 PUBLISHED 文档；</li>
 *   <li>{@code retention-cron}：按每 KB 策略 retentionDays 物理删除保留期到期 ARCHIVED 文档。</li>
 * </ul>
 * <p>需 @EnableScheduling（已加在 KnowledgeApplication）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GovernanceScheduleTask {

    private final KnowledgeGovernanceService governanceService;
    private final KnowledgeLifecycleService lifecycleService;
    private final GovernanceProperties properties;

    @Scheduled(cron = "${kb.governance.expire-cron:0 0 2 * * ?}")
    public void scanExpired() {
        if (!properties.isEnabled() || !properties.isScheduleEnabled()) {
            return;
        }
        log.info("[治理-定时] 开始扫描过期文档...");
        try {
            int count = governanceService.scanExpiredDocuments();
            log.info("[治理-定时] 扫描过期文档完成，处理 {} 篇", count);
        } catch (Exception e) {
            log.error("[治理-定时] 扫描过期文档异常", e);
        }
    }

    @Scheduled(cron = "${kb.governance.auto-archive-cron:0 30 2 * * ?}")
    public void autoArchive() {
        if (!properties.isEnabled() || !properties.isScheduleEnabled()) {
            return;
        }
        log.info("[治理-定时] 开始自动归档...");
        try {
            int count = lifecycleService.autoArchive();
            log.info("[治理-定时] 自动归档完成，归档 {} 篇", count);
        } catch (Exception e) {
            log.error("[治理-定时] 自动归档异常", e);
        }
    }

    @Scheduled(cron = "${kb.governance.retention-cron:0 0 3 * * ?}")
    public void retentionPurge() {
        if (!properties.isEnabled() || !properties.isScheduleEnabled()) {
            return;
        }
        log.info("[治理-定时] 开始保留期硬删除...");
        try {
            int count = lifecycleService.purgeRetained();
            log.info("[治理-定时] 保留期硬删除完成，清理 {} 篇", count);
        } catch (Exception e) {
            log.error("[治理-定时] 保留期硬删除异常", e);
        }
    }
}
