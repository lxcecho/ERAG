package com.knowledge.kb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.kb.entity.KbDocAcl;
import com.knowledge.kb.permission.AclEffect;
import com.knowledge.kb.permission.AclSubjectType;
import com.knowledge.kb.permission.DocPermission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档 ACL 服务：负责 ACL 的 CRUD +「给定用户对某文档的 allow/deny 集合」查询。
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbDocAclService extends IService<KbDocAcl> {

    /**
     * 批量查询「某用户对多个文档」的 ACL allow/deny 结果。
     * <p>主体展开：U + R（用户所有角色）+ D（用户部门 + 祖先部门）。
     *
     * @return Map<docId, Map<permissionCode, effect(A/D)>>，其中 DENY 优先级高于 ALLOW；
     *         若某文档某权限不在 map 中表示 ACL 未覆盖。
     */
    Map<Long, Map<String, AclEffect>> batchQueryEffect(Long tenantId, Long userId, Set<Long> docIds);

    /** 查询某文档某主体某权限的 allow/deny（含过期剔除） */
    AclEffect queryOne(Long docId, AclSubjectType subjectType, Long subjectId, DocPermission permission);

    /** 新增 ACL（唯一键冲突时抛 BizException，避免歧义） */
    void grant(Long docId, AclSubjectType subjectType, Long subjectId, DocPermission permission,
               AclEffect effect, Long grantBy, LocalDateTime expireTime);

    /** 撤销 ACL（软删） */
    void revoke(Long docId, AclSubjectType subjectType, Long subjectId, DocPermission permission);

    /** 获取用户所属的所有 roleIds（sys_user_role）——R 类型主体展开用 */
    List<Long> listUserRoleIds(Long userId);

    /** 获取用户所属部门 + 祖先部门 ID（D 类型主体展开用；当前未实现组织架构时返回空） */
    List<Long> listUserDeptIdsWithAncestors(Long userId);
}
