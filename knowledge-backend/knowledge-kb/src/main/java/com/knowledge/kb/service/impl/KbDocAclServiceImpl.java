package com.knowledge.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.entity.KbDocAcl;
import com.knowledge.kb.mapper.KbDocAclMapper;
import com.knowledge.kb.permission.AclEffect;
import com.knowledge.kb.permission.AclSubjectType;
import com.knowledge.kb.permission.DocPermission;
import com.knowledge.kb.service.KbDocAclService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档 ACL 服务实现。
 * <p>关键点：
 * 1) 所有查询带 tenant_id 条件（即使 MP 拦截器补也手动补，双重安全）
 * 2) 唯一键冲突时直接抛异常（避免 allow+deny 双记录造成算法歧义）
 * 3) 查询时自动剔除 expired ACL（NOW() < expire_time OR expire_time IS NULL）
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocAclServiceImpl extends ServiceImpl<KbDocAclMapper, KbDocAcl> implements KbDocAclService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, Map<String, AclEffect>> batchQueryEffect(Long tenantId, Long userId, Set<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) return Collections.emptyMap();
        Long tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        if (tid == null) return Collections.emptyMap();

        // 1) 构造主体集合：用户本人 + 全部角色 + 全部部门(含祖先)
        List<Object[]> subjectPairs = new ArrayList<>();
        subjectPairs.add(new Object[]{"U", userId});
        for (Long roleId : listUserRoleIds(userId)) {
            subjectPairs.add(new Object[]{"R", roleId});
        }
        for (Long deptId : listUserDeptIdsWithAncestors(userId)) {
            subjectPairs.add(new Object[]{"D", deptId});
        }
        if (subjectPairs.isEmpty()) return Collections.emptyMap();

        // 2) 拼 SQL：(doc_id IN (...) ) AND (subject_type,subject_id) IN ... AND tenant_id=? AND deleted=0
        StringBuilder sb = new StringBuilder(256);
        List<Object> args = new ArrayList<>();
        sb.append("SELECT doc_id, permission, effect FROM kb_doc_acl WHERE tenant_id=? AND deleted=0 ");
        args.add(tid);
        sb.append("AND doc_id IN (");
        for (Long d : docIds) { sb.append("?,"); args.add(d); }
        sb.setLength(sb.length() - 1);
        sb.append(") AND (subject_type, subject_id) IN (");
        for (Object[] p : subjectPairs) { sb.append("(?,?),"); args.add(p[0]); args.add(p[1]); }
        sb.setLength(sb.length() - 1);
        sb.append(") AND (expire_time IS NULL OR expire_time > NOW())");

        // 3) 聚合：对每个 (doc, perm)，若出现 DENY 则 DENY；否则有 ALLOW 就 ALLOW（DENY 优先）
        Map<Long, Map<String, AclEffect>> result = new HashMap<>();
        try {
            jdbcTemplate.query(sb.toString(), rs -> {
                long docId = rs.getLong(1);
                String perm = rs.getString(2);
                String eff = rs.getString(3);
                Map<String, AclEffect> perms = result.computeIfAbsent(docId, k -> new HashMap<>());
                AclEffect prev = perms.get(perm);
                AclEffect now = "D".equalsIgnoreCase(eff) ? AclEffect.DENY : AclEffect.ALLOW;
                if (prev == null || now == AclEffect.DENY) {
                    // DENY 永远覆盖 ALLOW；ALLOW 不覆盖已有的 DENY
                    perms.put(perm, now);
                }
            }, args.toArray());
        } catch (Exception e) {
            log.warn("[KbDocAcl] batchQueryEffect 异常: {}", e.getMessage());
            return Collections.emptyMap();
        }
        return result;
    }

    @Override
    public AclEffect queryOne(Long docId, AclSubjectType subjectType, Long subjectId, DocPermission permission) {
        if (docId == null || subjectType == null || subjectId == null || permission == null) return null;
        Long tid = TenantContext.requiredTenantId();
        LocalDateTime now = LocalDateTime.now();
        KbDocAcl acl = getOne(new LambdaQueryWrapper<KbDocAcl>()
                .eq(KbDocAcl::getTenantId, tid)
                .eq(KbDocAcl::getDocId, docId)
                .eq(KbDocAcl::getSubjectType, subjectType.getCode())
                .eq(KbDocAcl::getSubjectId, subjectId)
                .eq(KbDocAcl::getPermission, permission.getCode())
                .and(w -> w.isNull(KbDocAcl::getExpireTime).or().gt(KbDocAcl::getExpireTime, now))
                .last("LIMIT 1"));
        return acl == null ? null : AclEffect.of(acl.getEffect());
    }

    @Override
    public void grant(Long docId, AclSubjectType subjectType, Long subjectId, DocPermission permission,
                      AclEffect effect, Long grantBy, LocalDateTime expireTime) {
        if (docId == null || subjectType == null || subjectId == null || permission == null || effect == null) {
            throw new BizException(400, "授权参数不完整");
        }
        Long tid = TenantContext.requiredTenantId();
        try {
            KbDocAcl acl = new KbDocAcl();
            acl.setTenantId(tid);
            acl.setDocId(docId);
            acl.setSubjectType(subjectType.getCode());
            acl.setSubjectId(subjectId);
            acl.setPermission(permission.getCode());
            acl.setEffect(effect.getCode());
            acl.setGrantBy(grantBy);
            acl.setExpireTime(expireTime);
            save(acl);
        } catch (Exception e) {
            // 唯一键冲突：说明 (tenant, doc, subjectType, subjectId, permission, deleted=0) 已有记录，
            // 先删除软删的再新增，或直接抛错提示前端
            throw new BizException(409, "该主体对该权限已存在授权记录，请先撤销再授权");
        }
    }

    @Override
    public void revoke(Long docId, AclSubjectType subjectType, Long subjectId, DocPermission permission) {
        Long tid = TenantContext.requiredTenantId();
        KbDocAcl acl = getOne(new LambdaQueryWrapper<KbDocAcl>()
                .eq(KbDocAcl::getTenantId, tid)
                .eq(KbDocAcl::getDocId, docId)
                .eq(KbDocAcl::getSubjectType, subjectType.getCode())
                .eq(KbDocAcl::getSubjectId, subjectId)
                .eq(KbDocAcl::getPermission, permission.getCode())
                .eq(KbDocAcl::getDeleted, 0)
                .last("LIMIT 1"));
        if (acl != null) {
            removeById(acl.getId()); // TableLogic 软删
        }
    }

    /** 取用户所属角色：查 sys_user_role（跨模块直接 JDBC 简单高效） */
    @Override
    public List<Long> listUserRoleIds(Long userId) {
        if (userId == null) return List.of();
        Long tid = TenantContext.requiredTenantId();
        try {
            return jdbcTemplate.query(
                    "SELECT role_id FROM sys_user_role WHERE tenant_id=? AND user_id=?",
                    (rs, i) -> rs.getLong(1), tid, userId);
        } catch (Exception e) {
            log.warn("[KbDocAcl] listUserRoleIds 失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /** 部门 + 祖先：当前项目未引入 sys_dept 组织架构树，返回空集合（未来扩展：闭包表 / N+1 递归） */
    @Override
    public List<Long> listUserDeptIdsWithAncestors(Long userId) {
        return List.of();
    }

    // 防止未使用警告（HashSet import 为扩展预留）
    @SuppressWarnings("unused")
    private static final Set<String> _UNUSED = new HashSet<>();
}
