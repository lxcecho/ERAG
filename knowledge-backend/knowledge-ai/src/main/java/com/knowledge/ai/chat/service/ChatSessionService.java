package com.knowledge.ai.chat.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.ai.chat.dto.ChatSessionVo;
import com.knowledge.ai.chat.entity.ChatSession;

/**
 * 聊天会话服务
 *
 * @author: lxcechoo@gmail.com
 */
public interface ChatSessionService extends IService<ChatSession> {

    /** 分页查询当前用户在指定知识库下的会话 */
    IPage<ChatSessionVo> page(Long kbId, Long userId, Integer pageNo, Integer pageSize);

    /** 创建会话，返回会话ID */
    Long create(Long kbId, Long userId, String title);

    /** 重命名会话 */
    void rename(Long id, String title, Long userId);

    /** 删除会话（软删，同时清理消息） */
    void remove(Long id, Long userId);

    /**
     * 确保会话存在：sessionId 为空则新建（标题取问题前若干字），否则校验归属。
     *
     * @return 有效的会话ID
     */
    Long ensureSession(Long sessionId, Long kbId, Long userId, String title);
}
