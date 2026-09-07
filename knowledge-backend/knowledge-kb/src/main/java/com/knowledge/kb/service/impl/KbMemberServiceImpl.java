package com.knowledge.kb.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.constant.KbRole;
import com.knowledge.kb.dto.KbMemberVo;
import com.knowledge.kb.entity.KbMember;
import com.knowledge.kb.mapper.KbMemberMapper;
import com.knowledge.kb.service.KbMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 知识库成员服务实现
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbMemberServiceImpl extends ServiceImpl<KbMemberMapper, KbMember>
        implements KbMemberService {

    @Override
    public void addMember(Long kbId, Long userId, String role) {
        KbRole kbRole = KbRole.of(role);
        if (kbRole == null) {
            throw new BizException("非法的角色: " + role);
        }
        if (kbRole == KbRole.OWNER) {
            throw new BizException("不能直接添加 owner，每个知识库仅一个 owner");
        }
        // 唯一键 uk_kb_user 防重复；若已存在则更新角色
        KbMember exist = lambdaQuery()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId)
                .one();
        if (exist != null) {
            exist.setRole(kbRole.getCode());
            updateById(exist);
        } else {
            KbMember member = new KbMember();
            member.setKbId(kbId);
            member.setUserId(userId);
            member.setRole(kbRole.getCode());
            save(member);
        }
        log.info("[成员管理] kb={} 添加成员 user={} role={}", kbId, userId, role);
    }

    @Override
    public void removeMember(Long kbId, Long memberId) {
        KbMember member = getById(memberId);
        if (member == null || !member.getKbId().equals(kbId)) {
            throw new BizException("成员不存在");
        }
        if (KbRole.OWNER.getCode().equalsIgnoreCase(member.getRole())) {
            throw new BizException("不能移除 owner");
        }
        removeById(memberId);
        log.info("[成员管理] kb={} 移除成员 member={}", kbId, memberId);
    }

    @Override
    public void updateRole(Long kbId, Long memberId, String role) {
        KbRole kbRole = KbRole.of(role);
        if (kbRole == null || kbRole == KbRole.OWNER) {
            throw new BizException("非法的目标角色（不能设为 owner）");
        }
        KbMember member = getById(memberId);
        if (member == null || !member.getKbId().equals(kbId)) {
            throw new BizException("成员不存在");
        }
        if (KbRole.OWNER.getCode().equalsIgnoreCase(member.getRole())) {
            throw new BizException("不能修改 owner 角色");
        }
        member.setRole(kbRole.getCode());
        updateById(member);
    }

    @Override
    public IPage<KbMemberVo> page(Long kbId, Integer pageNo, Integer pageSize) {
        Page<KbMemberVo> page = new Page<>(
                pageNo == null || pageNo < 1 ? 1 : pageNo,
                pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100));
        return baseMapper.selectMemberPage(page, kbId);
    }

    @Override
    public void addOwner(Long kbId, Long userId) {
        KbMember member = new KbMember();
        member.setKbId(kbId);
        member.setUserId(userId);
        member.setRole(KbRole.OWNER.getCode());
        save(member);
    }
}
