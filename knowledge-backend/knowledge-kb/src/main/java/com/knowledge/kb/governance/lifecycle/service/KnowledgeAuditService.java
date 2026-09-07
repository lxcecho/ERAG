package com.knowledge.kb.governance.lifecycle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.kb.governance.lifecycle.dto.AuditQuery;
import com.knowledge.kb.governance.lifecycle.dto.AuditVo;

/**
 * 知识审计门面：统一治理动作审计（生命周期/版本/策略/归档）写入与查询。
 * <p>复用 {@code kb_doc_audit_log} 表（与既有访问审计同表，扩展 action 词表），不新建大表。
 *
 * @author: lxcechoo@gmail.com
 */
public interface KnowledgeAuditService {

    /** 审计动作常量（扩展既有 VIEW/DOWNLOAD/EDIT/DELETE/SHARE/SEARCH_HIT 词表） */
    String ACTION_LIFECYCLE_SUBMIT = "LIFECYCLE_SUBMIT";
    String ACTION_LIFECYCLE_APPROVE = "LIFECYCLE_APPROVE";
    String ACTION_LIFECYCLE_REJECT = "LIFECYCLE_REJECT";
    String ACTION_PUBLISH = "PUBLISH";
    String ACTION_ARCHIVE = "ARCHIVE";
    String ACTION_RESTORE = "RESTORE";
    String ACTION_VERSION_ROLLBACK = "VERSION_ROLLBACK";
    String ACTION_POLICY_UPDATE = "POLICY_UPDATE";
    String ACTION_RETENTION_PURGE = "RETENTION_PURGE";

    /** 结果：A=放行 D=拒绝 */
    String RESULT_ALLOW = "A";
    String RESULT_DENY = "D";

    /**
     * 同步写入审计日志（显式传 tenantId，兼容定时任务无 TenantContext 场景）。
     *
     * @param tenantId    租户ID
     * @param userId      操作人ID
     * @param docId       文档ID（策略审计可空）
     * @param action      动作（见常量）
     * @param pass        是否放行
     * @param passReason  放行原因
     * @param denyReason  拒绝原因
     */
    void record(Long tenantId, Long userId, Long docId, String action, boolean pass,
                String passReason, String denyReason);

    /**
     * 审计分页查询（按 kbId/docId/action/result/userId/时间过滤，tenantId 由 TenantContext 注入）。
     */
    IPage<AuditVo> auditPage(AuditQuery query);
}
