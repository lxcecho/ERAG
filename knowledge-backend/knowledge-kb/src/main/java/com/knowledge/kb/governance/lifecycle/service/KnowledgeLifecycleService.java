package com.knowledge.kb.governance.lifecycle.service;

import com.knowledge.kb.governance.lifecycle.dto.LifecycleActionRequest;
import com.knowledge.kb.governance.lifecycle.dto.LifecycleVo;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleAction;

/**
 * 知识生命周期服务：顶层治理状态机驱动层。
 * <p>核心职责：
 * <ul>
 *   <li>{@link #transition} 校验权限 → 加载文档 → 状态机裁定 → 桥接同步 review_status → 落库 → 写审核流水+审计；</li>
 *   <li>{@link #getState} 查询文档当前生命周期视图；</li>
 *   <li>{@link #autoArchive} 定时调用：按策略 autoArchiveDays 归档超期 PUBLISHED 文档；</li>
 *   <li>{@link #purgeRetained} 定时调用：按策略 retentionDays 物理删除保留期到期 ARCHIVED 文档并级联清理。</li>
 * </ul>
 * <p>非破坏：旧 {@code KnowledgeGovernanceServiceImpl.review()} 不动；本服务在 REVIEW 阶段同时写一条
 * {@code document_review} 流水，保证旧审核分页仍可见。
 *
 * @author: lxcechoo@gmail.com
 */
public interface KnowledgeLifecycleService {

    /**
     * 执行生命周期迁移。
     *
     * @param request 迁移请求（docId / action / comment）
     * @param userId  操作人ID
     */
    void transition(LifecycleActionRequest request, Long userId);

    /**
     * 便捷重载：编程式调用（如版本回滚触发 UPDATE 重审）。
     *
     * @param docId   文档ID
     * @param action  生命周期动作
     * @param userId  操作人ID
     * @param comment 备注（审核意见/归档原因）
     */
    void transition(Long docId, LifecycleAction action, Long userId, String comment);

    /**
     * 查询文档当前生命周期视图（含桥接后的 review_status / 归档时间等）。
     */
    LifecycleVo getState(Long docId);

    /**
     * 定时任务：自动归档。按每 KB 策略 autoArchiveDays，将以 reviewedAt 为发布时间代理、
     * 发布后超过 autoArchiveDays 的 PUBLISHED 文档迁移至 ARCHIVED。
     *
     * @return 本次归档文档数
     */
    int autoArchive();

    /**
     * 定时任务：保留期硬删除。按每 KB 策略 retentionDays，对 archivedAt + retentionDays &lt; now 的
     * ARCHIVED 文档物理删除 kb_document，并级联清理 document_version/fingerprint/quality/review/
     * kb_doc_acl/kb_doc_audit_log。删前写 RETENTION_PURGE 审计。逐条 best-effort。
     *
     * @return 本次清理文档数
     */
    int purgeRetained();
}
