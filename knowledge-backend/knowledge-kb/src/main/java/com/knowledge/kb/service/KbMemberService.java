package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.kb.dto.KbMemberVo;
import com.knowledge.kb.entity.KbMember;

/**
 * 知识库成员服务接口
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbMemberService extends IService<KbMember> {

    /** 添加成员（owner 专用） */
    void addMember(Long kbId, Long userId, String role);

    /** 移除成员（owner 专用，不能移除自己） */
    void removeMember(Long kbId, Long memberId);

    /** 修改成员角色（owner 专用） */
    void updateRole(Long kbId, Long memberId, String role);

    /** 成员分页列表 */
    IPage<KbMemberVo> page(Long kbId, Integer pageNo, Integer pageSize);

    /** 知识库创建时写入 owner 记录 */
    void addOwner(Long kbId, Long userId);
}
