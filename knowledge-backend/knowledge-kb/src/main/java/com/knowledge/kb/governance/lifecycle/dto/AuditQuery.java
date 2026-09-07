package com.knowledge.kb.governance.lifecycle.dto;

import com.knowledge.kb.governance.dto.GovernanceQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 知识审计分页查询条件。
 * <p>extends {@link GovernanceQuery}（含 kbId/docId/status/page）。
 * <p>tenantId 由 Service 从 TenantContext 注入（kb_doc_audit_log 不走行级租户拦截器）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditQuery extends GovernanceQuery {

    /** 操作人ID */
    private Long userId;

    /** 动作 VIEW/DOWNLOAD/.../LIFECYCLE_SUBMIT/PUBLISH/ARCHIVE/RESTORE/VERSION_ROLLBACK/POLICY_UPDATE */
    private String action;

    /** 结果 A=放行 D=拒绝 */
    private String result;

    /** 起始时间 */
    private LocalDateTime beginTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 租户ID（Service 注入，非前端入参） */
    private Long tenantId;
}
