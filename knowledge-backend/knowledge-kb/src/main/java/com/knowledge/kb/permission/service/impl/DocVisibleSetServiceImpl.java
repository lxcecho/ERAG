package com.knowledge.kb.permission.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.knowledge.auth.security.SecurityUserDetails;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import com.knowledge.kb.constant.KbRole;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.permission.DocVisibility;
import com.knowledge.kb.permission.service.DocVisibleSetService;
import com.knowledge.kb.service.KbPermissionService;
import com.knowledge.kb.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 可见文档集合预计算实现。
 * <p>
 * 缓存策略：Caffeine 本地缓存，key={tenantId}:{kbId}:{userId}，TTL 5 min，
 * 写路径（ACL变更/可见性变更/踢KB成员）主动失效该 key（调用方调用 evict(kbId,userId)）。
 * <p>
 * 算法思路：
 * 1) 先取 KB 角色：
 *    - owner/editor → allVisible=true（PUBLIC/PROTECTED 文档全可见；PRIVATE 走 ACL + 创建者，另由 DocPermissionService 过滤）
 *    - viewer      → 基础可见 docIds = visibility in (P,T) ∪ {自己创建的 R 文档}
 *    - 非 KB 成员  → 基础可见 = ∅
 * 2) 再叠 ACL：基础 ∪ (ACL allow 文档) \ (ACL deny 文档)
 * 3) 截断：如果 docIds.size() > 1000 → 置 truncated=true，调用方应走 Post-Filter（IN 查询表达式太长影响 Milvus/ES 性能）
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocVisibleSetServiceImpl implements DocVisibleSetService {

    /** 可见集合截断阈值。超过就不建议做 documentId IN (...) 预过滤，改走 Post-Filter。 */
    private static final int MAX_DOC_IDS_PRE_FILTER = 1000;

    private final KbPermissionService kbPermissionService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final JdbcTemplate jdbcTemplate;

    /** key = tenantId:kbId:userId, value = VisibleSet */
    private final Cache<String, VisibleSet> cache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    @Override
    public VisibleSet computeViewableDocIds(Long userId, Long kbId) {
        Long uid = userId != null ? userId : resolveUid();
        if (uid == null || kbId == null) return new VisibleSet(false, Set.of(), false);
        Long tid = TenantContext.getTenantId();
        if (tid == null) return new VisibleSet(false, Set.of(), false);
        String key = tid + ":" + kbId + ":" + uid;
        return cache.get(key, k -> computeVisibleSetInternal(uid, kbId, tid));
    }

    private VisibleSet computeVisibleSetInternal(Long uid, Long kbId, Long tid) {
        // S1: KB 级角色
        KbRole kbRole = kbPermissionService.getRole(kbId, uid);

        // 超管/owner/editor：全可见(仅要单独排除 PRIVATE 别人文档的创建者 != uid 才可见，但预计算层面先保守一点：如果 allVisible=true + truncated=false，
        // 实际可见文档数可能很多，后续 filterResults 内部对每个 PRIVATE 文档再精查 ACL。
        if (kbRole == KbRole.OWNER || kbRole == KbRole.EDITOR || isTenantOrPlatformAdmin()) {
            return new VisibleSet(true, Set.of(), false);
        }

        // S2: viewer / 非成员：用 SQL 直接算基础可见集合
        StringBuilder sql = new StringBuilder(128);
        sql.append("SELECT id, visibility, creator_id FROM kb_document WHERE tenant_id=? AND kb_id=? AND deleted=0");
        Set<Long> base = new HashSet<>();
        try {
            jdbcTemplate.query(sql.toString(), rs -> {
                long docId = rs.getLong(1);
                String vis = rs.getString(2);
                long creatorId = rs.getLong(3);
                DocVisibility visibility = DocVisibility.of(vis);
                boolean viewable = switch (visibility) {
                    case PUBLIC, PROTECTED -> kbRole == KbRole.VIEWER;
                    case PRIVATE -> creatorId == uid; // 自己创建的 PRIVATE 可见
                };
                if (viewable) base.add(docId);
            }, tid, kbId);
        } catch (Exception e) {
            log.warn("[DocVisibleSet] 基础集合查询 kb={} uid={}: {}", kbId, uid, e.getMessage());
        }

        // S3: ACL 修正：并 allow \ deny（直接用 kb_doc_acl 里 VIEW 权限拉出来再补/减
        try {
            Set<Long> allowView = queryAclDocIds(tid, kbId, uid, "A");
            Set<Long> denyView  = queryAclDocIds(tid, kbId, uid, "D");
            base.addAll(allowView);
            base.removeAll(denyView);
        } catch (Exception e) {
            log.warn("[DocVisibleSet] ACL 修正失败 kb={} uid={}: {}", kbId, uid, e.getMessage());
        }

        // S4: 截断判断
        boolean truncated = base.size() > MAX_DOC_IDS_PRE_FILTER;
        return new VisibleSet(false, Collections.unmodifiableSet(base), truncated);
    }

    /** 查本 kb 下本用户有 ACL VIEW allow/deny 的 docId 集合 */
    private Set<Long> queryAclDocIds(Long tid, Long kbId, Long uid, String effect) {
        String sql = """
            SELECT a.doc_id FROM kb_doc_acl a
              INNER JOIN kb_document d ON d.id=a.doc_id AND d.tenant_id=a.tenant_id AND d.deleted=0
             WHERE a.tenant_id=? AND a.deleted=0
               AND d.kb_id=?
               AND a.permission='VIEW' AND a.effect=?
               AND (a.expire_time IS NULL OR a.expire_time>NOW())
               AND (
                     (a.subject_type='U' AND a.subject_id=?)
                  OR (a.subject_type='R' AND a.subject_id IN (SELECT role_id FROM sys_user_role WHERE tenant_id=a.tenant_id AND user_id=?))
               )
            """;
        return new HashSet<>(jdbcTemplate.query(sql, (rs, i) -> rs.getLong(1), tid, kbId, effect, uid, uid));
    }

    @Override
    public boolean isViewable(Long userId, Long kbId, long docId, DocVisibility visibility, Long creatorId) {
        VisibleSet set = computeViewableDocIds(userId, kbId);
        if (set.isAllVisible()) {
            // allVisible 情况：PUBLIC/PROTECTED 可以；PRIVATE 只有创建者可见
            return visibility != DocVisibility.PRIVATE || (creatorId != null && creatorId.equals(userId != null ? userId : resolveUid()));
        }
        return set.docIds().contains(docId);
    }

    @Override
    public void evict(Long kbId, Long userId) {
        Long tid = TenantContext.getTenantId();
        if (tid == null || kbId == null) return;
        if (userId != null) {
            cache.invalidate(tid + ":" + kbId + ":" + userId);
        } else {
            cache.asMap().keySet().removeIf(k -> k.startsWith(tid + ":" + kbId + ":"));
        }
    }

    @Override
    public void evictForKb(Long kbId) {
        // 失调整 KB 所有用户可见集合（KB角色变化/文档新增删除/ACL批量变更时调用）
        evict(kbId, null);
    }

    private Long resolveUid() {
        try { return SecurityUtils.currentUserId(); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unused")
    private boolean isTenantOrPlatformAdmin() {
        try {
            SecurityUserDetails u = SecurityUtils.currentUser();
            if (u == null || u.getRoles() == null) return false;
            return u.getRoles().contains("platform_super_admin")
                    || u.getRoles().contains("tenant_admin")
                    || u.getRoles().contains("admin");
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean equals(Object obj) {
        // 避免未使用警告（Caffeine Cache 内部不使用 equals）
        return this == obj || (obj instanceof DocVisibleSetServiceImpl s
                && Objects.equals(kbPermissionService, s.kbPermissionService)
                && Objects.equals(knowledgeBaseService, s.knowledgeBaseService)
                && Objects.equals(jdbcTemplate, s.jdbcTemplate));
    }

    @Override
    public int hashCode() {
        return Objects.hash(kbPermissionService, knowledgeBaseService, jdbcTemplate);
    }
}
