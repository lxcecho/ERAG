package com.knowledge.kb.governance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识治理配置属性（读取 application.yml 中 kb.governance.* 配置）。
 * <p>集中管理去重阈值、检索门禁、定时任务开关等治理参数，调优无需改代码。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "kb.governance")
public class GovernanceProperties {

    /** 治理总开关：false 时跳过指纹 / 去重 / 评分埋点（降级为无治理） */
    private boolean enabled = true;

    /** 近似重复检测：SimHash Hamming 距离阈值，≤该值视为近似重复 */
    private int simhashThreshold = 3;

    /** 近似重复检测：仅与同 KB 内文档比对（true）；false 则全租户比对 */
    private boolean duplicateScopeKbOnly = true;

    /** 检索门禁：true 时 RAG 检索排除未审核（PENDING/REJECTED）文档 */
    private boolean reviewGate = false;

    /** 检索门禁：true 时 RAG 检索排除已过期文档（expire_at < now） */
    private boolean expireGate = false;

    /** 检索门禁：true 时 RAG 检索仅放行 PUBLISHED 文档（排除 DRAFT/REVIEW/ARCHIVED），非破坏默认关 */
    private boolean lifecycleGate = false;

    /** 定时任务开关：扫描过期文档 / 自动归档 / 保留期硬删除 */
    private boolean scheduleEnabled = true;

    /** 定时任务 cron：默认每天凌晨 2 点扫描过期文档 */
    private String expireCron = "0 0 2 * * ?";

    /** 定时任务 cron：默认每天凌晨 2:30 自动归档已发布超期文档（按 policy.autoArchiveDays） */
    private String autoArchiveCron = "0 30 2 * * ?";

    /** 定时任务 cron：默认每天凌晨 3 点清理保留期到期归档文档（按 policy.retentionDays 物理删） */
    private String retentionCron = "0 0 3 * * ?";
}
