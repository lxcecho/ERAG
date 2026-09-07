package com.knowledge.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.kb.dto.KbMemberVo;
import com.knowledge.kb.entity.KbMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识库成员 Mapper
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbMemberMapper extends BaseMapper<KbMember> {

    /**
     * 成员分页查询：左联 sys_user 取用户名/昵称。
     */
    @Select("""
            <script>
            SELECT m.id, m.kb_id AS kbId, m.user_id AS userId, m.role,
                   u.username, u.nickname,
                   m.create_time AS createTime
            FROM kb_member m
            LEFT JOIN sys_user u ON m.user_id = u.id AND u.deleted = 0
            WHERE m.kb_id = #{kbId}
            ORDER BY FIELD(m.role, 'owner', 'editor', 'viewer'), m.create_time ASC
            </script>
            """)
    IPage<KbMemberVo> selectMemberPage(IPage<KbMemberVo> page, @Param("kbId") Long kbId);
}
