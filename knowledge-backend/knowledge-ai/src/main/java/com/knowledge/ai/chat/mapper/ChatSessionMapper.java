package com.knowledge.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.ai.chat.dto.ChatSessionVo;
import com.knowledge.ai.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 聊天会话 Mapper
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 分页查询指定用户在某知识库下的会话（按更新时间倒序）。
     */
    @Select("""
            <script>
            SELECT id, kb_id AS kbId, user_id AS userId, title,
                   create_time AS createTime, update_time AS updateTime
            FROM chat_session
            WHERE deleted = 0
              <if test="kbId != null"> AND kb_id = #{kbId} </if>
              <if test="userId != null"> AND user_id = #{userId} </if>
            ORDER BY update_time DESC
            </script>
            """)
    IPage<ChatSessionVo> selectSessionPage(IPage<ChatSessionVo> page,
                                           @Param("kbId") Long kbId,
                                           @Param("userId") Long userId);
}
