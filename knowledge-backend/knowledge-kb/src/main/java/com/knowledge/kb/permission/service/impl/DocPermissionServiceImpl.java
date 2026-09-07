package com.knowledge.kb.permission.service.impl;

import com.knowledge.auth.security.SecurityUserDetails;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.constant.KbRole;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.permission.AclEffect;
import com.knowledge.kb.permission.DocPermission;
import com.knowledge.kb.permission.DocVisibility;
import com.knowledge.kb.permission.service.DocPermissionService;
import com.knowledge.kb.permission.service.DocVisibleSetService;
import com.knowledge.kb.service.KbDocAclService;
import com.knowledge.kb.service.KbDocAuditLogService;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 文档级权限服务实现（权限算法 S0-S7 实现 + 批量过滤 + 审计）。
 *
 * <h3>固定算法顺序（DENY 优先 → ALLOW → 继承 → 创建者 → 角色 × 可见性 → 缺省拒绝）</h3>
 * <pre>
 * S0 租户屏障：doc.tenantId ≠ 上下文 tid → DENY
 * S1 超管短路：platform/tenant admin → ALLOW（审计 pass=TENANT_ADMIN）
 * S2 DENY 命中 → DENY（审计 deny=DENY_RULE）
 * S3 ALLOW 命中 → ALLOW（审计 pass=ACL_ALLOW）
 * S4 inherit=false → DENY（审计 deny=NO_INHERIT）
 * S5 创建者兜底：doc.creatorId == uid → ALLOW（审计 pass=CREATOR）
 * S6 KB 角色 × 可见性：
 *      visibility=PRIVATE → DENY（仅 S2~S5 放行，这里不通过；deny=PRIVATE_DOC）
 *      visibility ∈ {P, T}:
 *        owner/editor → ALLOW（pass=KB_OWNER/KB_EDITOR）
 *        viewer + action ∈ {VIEW, DOWNLOAD, SHARE} → ALLOW（pass=KB_VIEWER）
 *        viewer + EDIT/DELETE → DENY（deny=OUT_OF_KB_ROLE）
 * S7 默认 → DENY
 * </pre>
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPermissionServiceImpl implements DocPermissionService {

    private final KbPermissionService kbPermissionService;
    private final KbDocAclService aclService;
    private final KbDocAuditLogService auditLogService;
    private final KbDocumentService documentService;
    private final DocVisibleSetService visibleSetService;

    // ==================== 单点判断 ====================

    @Override
    public boolean canAccess(Long userId, KbDocument doc, DocPermission action) {
        return judgeInternal(userId, doc, action).pass;
    }

    @Override
    public void requiredAccess(Long userId, KbDocument doc, DocPermission action) {
        JudgeResult r = judgeInternal(userId, doc, action);
        // required 不通过也要写 deny 审计，再抛 403
        Long uid = userId != null ? userId : resolveUid();
        if (!r.pass) {
            auditLogService.asyncAudit(uid, doc.getId(), action.getCode(), false, null, r.reason);
            throw new BizException(403, "无文档" + action.getLabel() + "权限：" + r.reason);
        }
        auditLogService.asyncAudit(uid, doc.getId(), action.getCode(), true, r.reason, null);
    }

    // ==================== 批量过滤：文档集合过滤（RAG Post-Filter 在业务层组合） ====================

    @Override
    public Set<Long> filterDocIds(Long userId, Long kbId, Collection<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) return Set.of();
        Long uid = userId != null ? userId : resolveUid();
        if (uid == null) return Set.of();
        Set<Long> idSet = new HashSet<>(docIds);
        Map<Long, Map<String, AclEffect>> aclMap = aclService.batchQueryEffect(TenantContext.getTenantId(), uid, idSet);
        Map<Long, KbDocument> docMap = new HashMap<>(idSet.size());
        for (KbDocument d : documentService.listByIds(idSet)) docMap.put(d.getId(), d);
        KbRole kbRole = kbId == null ? null : kbPermissionService.getRole(kbId, uid);
        boolean isAdmin = isTenantOrPlatformAdmin();
        Set<Long> pass = new HashSet<>();
        for (Long id : docIds) {
            KbDocument d = docMap.get(id);
            if (d == null) continue;
            JudgeResult jr = judge0(d, DocPermission.VIEW, uid, kbId, kbRole, isAdmin, aclMap, false);
            if (jr.pass) pass.add(id);
        }
        return Collections.unmodifiableSet(pass);
    }

    @Override
    public void evictCacheForDocPermissionChange(Long kbId, Long userId) {
        if (kbId == null) return;
        // userId=null 表示整 KB 失效（ACL 批量变更、删除文档等）
        if (userId == null) {
            visibleSetService.evictForKb(kbId);
        } else {
            visibleSetService.evict(kbId, userId);
        }
    }

    // ==================== 核心算法实现 ====================

    private JudgeResult judgeInternal(Long userId, KbDocument doc, DocPermission act) {
        Long uid = userId != null ? userId : resolveUid();
        if (doc == null || uid == null) return new JudgeResult(false, "NO_USER_OR_DOC");
        Long kbId = doc.getKbId();
        KbRole kbRole = kbPermissionService.getRole(kbId, uid);
        Map<Long, Map<String, AclEffect>> aclMap = new HashMap<>(1);
        aclMap.putAll(aclService.batchQueryEffect(TenantContext.getTenantId(), uid, Set.of(doc.getId())));
        return judge0(doc, act, uid, kbId, kbRole, isTenantOrPlatformAdmin(), aclMap, true);
    }

    /**
     * 核心权限算法。
     *
     * @param writeAudit 是否写审计；批量过滤时传 false（业务侧按需写 SEARCH_HIT / DOWNLOAD 审计）
     */
    private JudgeResult judge0(KbDocument doc, DocPermission act, Long uid, Long kbId,
                               KbRole kbRole, boolean isAdmin,
                               Map<Long, Map<String, AclEffect>> aclEffectMap,
                               boolean writeAudit) {
        // S0：租户屏障
        Long tid = TenantContext.getTenantId();
        if (doc.getTenantId() != null && tid != null && !doc.getTenantId().equals(tid)) {
            return new JudgeResult(false, "OUT_OF_TENANT");
        }
        // S1：超管短路
        if (isAdmin) {
            if (writeAudit) audit(uid, doc.getId(), act, true, "TENANT_ADMIN", null);
            return new JudgeResult(true, "TENANT_ADMIN");
        }
        // 取该文档 ACL 效果映射
        Map<String, AclEffect> permEffect = aclEffectMap == null ? Map.of()
                : aclEffectMap.getOrDefault(doc.getId(), Map.of());
        AclEffect aclEffect = permEffect.get(act.getCode());

        // S2：DENY 优先
        if (aclEffect == AclEffect.DENY) {
            if (writeAudit) audit(uid, doc.getId(), act, false, null, "DENY_RULE");
            return new JudgeResult(false, "DENY_RULE");
        }
        // S3：ALLOW 命中（含 ROLE/DEPT 主体间接命中）
        if (aclEffect == AclEffect.ALLOW) {
            if (writeAudit) audit(uid, doc.getId(), act, true, "ACL_ALLOW", null);
            return new JudgeResult(true, "ACL_ALLOW");
        }
        // S4：inherit=false → 不继承 KB 级角色权限；走 S5 创建者兜底
        boolean inherit = doc.getInheritKbPermission() == null || doc.getInheritKbPermission() == 1;
        if (!inherit) {
            if (writeAudit) audit(uid, doc.getId(), act, false, null, "NO_INHERIT");
            return new JudgeResult(false, "NO_INHERIT");
        }
        // S5：创建者兜底（创建者对其所有文档有完整权限，除非 S2 DENY 命中）
        if (doc.getCreatorId() != null && doc.getCreatorId().equals(uid)) {
            if (writeAudit) audit(uid, doc.getId(), act, true, "CREATOR", null);
            return new JudgeResult(true, "CREATOR");
        }
        // S6：KB 角色 × 文档可见性
        DocVisibility vis = DocVisibility.of(doc.getVisibility());
        if (vis == DocVisibility.PRIVATE) {
            if (writeAudit) audit(uid, doc.getId(), act, false, null, "PRIVATE_DOC");
            return new JudgeResult(false, "PRIVATE_DOC");
        }
        // PUBLIC / PROTECTED
        if (kbRole == KbRole.OWNER) {
            if (writeAudit) audit(uid, doc.getId(), act, true, "KB_OWNER", null);
            return new JudgeResult(true, "KB_OWNER");
        }
        if (kbRole == KbRole.EDITOR) {
            if (writeAudit) audit(uid, doc.getId(), act, true, "KB_EDITOR", null);
            return new JudgeResult(true, "KB_EDITOR");
        }
        if (kbRole == KbRole.VIEWER) {
            if (act == DocPermission.VIEW || act == DocPermission.DOWNLOAD || act == DocPermission.SHARE) {
                if (writeAudit) audit(uid, doc.getId(), act, true, "KB_VIEWER", null);
                return new JudgeResult(true, "KB_VIEWER");
            }
            if (writeAudit) audit(uid, doc.getId(), act, false, null, "OUT_OF_KB_ROLE");
            return new JudgeResult(false, "OUT_OF_KB_ROLE");
        }
        // S7：非 KB 成员 → 缺省拒绝
        if (writeAudit) audit(uid, doc.getId(), act, false, null, "OUT_OF_KB_MEMBER");
        return new JudgeResult(false, "OUT_OF_KB_MEMBER");
    }

    // ==================== 辅助 ====================

    private void audit(Long uid, Long docId, DocPermission action, boolean pass,
                       String passReason, String denyReason) {
        auditLogService.asyncAudit(uid, docId, action.getCode(), pass, passReason, denyReason);
    }

    private Long resolveUid() {
        try { return SecurityUtils.currentUserId(); } catch (Exception e) { return null; }
    }

    private boolean isTenantOrPlatformAdmin() {
        try {
            SecurityUserDetails u = SecurityUtils.currentUser();
            if (u == null || u.getRoles() == null) return false;
            return u.getRoles().contains("platform_super_admin")
                    || u.getRoles().contains("tenant_admin")
                    || u.getRoles().contains("admin");
        } catch (Exception e) { return false; }
    }

    /** 判定结果（内部值对象） */
    private record JudgeResult(boolean pass, String reason) {}
}
