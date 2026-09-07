package com.knowledge.kb.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.auth.security.SecurityUserDetails;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.constant.KbConstants;
import com.knowledge.kb.dto.KbCreateRequest;
import com.knowledge.kb.dto.KbPageQuery;
import com.knowledge.kb.dto.KbUpdateRequest;
import com.knowledge.kb.entity.KbMember;
import com.knowledge.kb.entity.KnowledgeBase;
import com.knowledge.kb.mapper.KnowledgeBaseMapper;
import com.knowledge.kb.service.KbMemberService;
import com.knowledge.kb.service.KbPermissionService;
import com.knowledge.kb.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库服务实现
 * <p>权限集成：创建时写入 owner 成员；分页仅返回当前用户参与的知识库（admin 全见）；
 * 删除需 owner 权限并级联清理成员关系。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {

    private final KbMemberService kbMemberService;
    private final KbPermissionService kbPermissionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KbCreateRequest request, Long ownerId) {
        KnowledgeBase kb = new KnowledgeBase();
        // 多租户：兜底写 tenantId。MP 拦截器在 INSERT 时也会自动注入，但 Service 层双写更稳。
        kb.setTenantId(com.knowledge.common.context.TenantContext.requiredTenantId());
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setOwnerId(ownerId);
        kb.setDocCount(0);
        kb.setStatus(KbConstants.KB_STATUS_NORMAL);
        save(kb);
        // 同步写入 owner 成员记录，建立权限关系
        kbMemberService.addOwner(kb.getId(), ownerId);
        log.info("[知识库创建] kb={} owner={}", kb.getId(), ownerId);
        return kb.getId();
    }

    @Override
    @CacheEvict(value = "kb", key = "'info:' + #id")
    public void update(Long id, KbUpdateRequest request) {
        KnowledgeBase kb = getById(id);
        if (kb == null) {
            throw new BizException("知识库不存在");
        }
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            kb.setStatus(request.getStatus());
        }
        updateById(kb);
    }

    /**
     * 分页查询：admin 可见全部；普通用户仅可见自己有成员关系的知识库。
     * <p>通过 inSql 子查询过滤（userId 为 Long 类型，无注入风险）。
     */
    @Override
    public IPage<KnowledgeBase> page(KbPageQuery query, Long userId) {
        boolean admin = isAdmin();
        return lambdaQuery()
                .like(query.getName() != null && !query.getName().isBlank(),
                        KnowledgeBase::getName, query.getName())
                .eq(query.getStatus() != null, KnowledgeBase::getStatus, query.getStatus())
                .and(!admin, w -> w.inSql(KnowledgeBase::getId,
                        "SELECT kb_id FROM kb_member WHERE user_id = " + userId))
                .orderByDesc(KnowledgeBase::getCreateTime)
                .page(query.toPage());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "kb", key = "'info:' + #id")
    public void remove(Long id, Long userId) {
        KnowledgeBase kb = getById(id);
        if (kb == null) {
            throw new BizException("知识库不存在");
        }
        // owner 权限校验（admin 绕过）
        kbPermissionService.checkOwner(id, userId);
        // 级联清理成员关系
        kbMemberService.lambdaUpdate()
                .eq(KbMember::getKbId, id)
                .remove();
        removeById(id);
        log.info("[知识库删除] kb={} operator={}", id, userId);
    }

    @Override
    public void incrDocCount(Long kbId) {
        lambdaUpdate()
                .setSql("doc_count = doc_count + 1")
                .eq(KnowledgeBase::getId, kbId)
                .update();
    }

    @Override
    public void decrDocCount(Long kbId) {
        lambdaUpdate()
                .setSql("doc_count = GREATEST(doc_count - 1, 0)")
                .eq(KnowledgeBase::getId, kbId)
                .update();
    }

    /**
     * 按 ID 查询（带 Redis 缓存，TTL=30min）。
     * <p>Spring AOP 代理拦截：外部调用（Controller）走缓存；内部调用（update/remove）走 super.getById 直查。
     */
    @Cacheable(value = "kb", key = "'info:' + #id", unless = "#result == null")
    public KnowledgeBase getById(Long id) {
        return super.getById(id);
    }

    /** 当前登录用户是否为超级管理员 */
    private boolean isAdmin() {
        try {
            SecurityUserDetails user = SecurityUtils.currentUser();
            return user.getRoles() != null && user.getRoles().contains("admin");
        } catch (Exception e) {
            return false;
        }
    }
}
