package com.knowledge.ai.chat.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.ai.chat.dto.ChatSessionVo;
import com.knowledge.ai.chat.entity.ChatSession;
import com.knowledge.ai.chat.mapper.ChatSessionMapper;
import com.knowledge.ai.chat.service.ChatMessageService;
import com.knowledge.ai.chat.service.ChatSessionService;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.entity.KnowledgeBase;
import com.knowledge.kb.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 聊天会话服务实现
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatSessionService {

    private final ChatMessageService chatMessageService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public IPage<ChatSessionVo> page(Long kbId, Long userId, Integer pageNo, Integer pageSize) {
        Page<ChatSessionVo> page = new Page<>(
                pageNo == null || pageNo < 1 ? 1 : pageNo,
                pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100));
        IPage<ChatSessionVo> result = baseMapper.selectSessionPage(page, kbId, userId);
        // 批量填充关联知识库名称（普通对话 kbId=0 不显示）
        List<ChatSessionVo> records = result.getRecords();
        if (records != null && !records.isEmpty()) {
            Set<Long> kbIds = records.stream()
                    .map(ChatSessionVo::getKbId)
                    .filter(id -> id != null && id > 0)
                    .collect(Collectors.toSet());
            if (!kbIds.isEmpty()) {
                Map<Long, String> nameMap = knowledgeBaseService.listByIds(kbIds).stream()
                        .collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName, (a, b) -> a));
                records.forEach(vo -> {
                    if (vo.getKbId() != null && vo.getKbId() > 0) {
                        vo.setKbName(nameMap.get(vo.getKbId()));
                    }
                });
            }
        }
        return result;
    }

    @Override
    public Long create(Long kbId, Long userId, String title) {
        ChatSession session = new ChatSession();
        // 多租户：兜底写 tenantId（MP 拦截器 INSERT 同样会注入）
        session.setTenantId(com.knowledge.common.context.TenantContext.requiredTenantId());
        session.setKbId(kbId);
        session.setUserId(userId);
        session.setTitle(title == null || title.isBlank() ? "新对话" : title);
        save(session);
        return session.getId();
    }

    @Override
    public void rename(Long id, String title, Long userId) {
        ChatSession session = getOwnedSession(id, userId);
        session.setTitle(title);
        updateById(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id, Long userId) {
        getOwnedSession(id, userId);
        // 先清理消息，再软删会话
        chatMessageService.removeBySession(id);
        removeById(id);
    }

    @Override
    public Long ensureSession(Long sessionId, Long kbId, Long userId, String title) {
        if (sessionId != null) {
            // 校验归属，防止越权访问他人会话
            getOwnedSession(sessionId, userId);
            return sessionId;
        }
        // 新会话：标题取问题前 20 字
        String t = title == null ? "新对话" : (title.length() > 20 ? title.substring(0, 20) + "..." : title);
        // 普通对话（kbId 为空）时归属记为 0（平台保留），chat_session.kb_id 为 NOT NULL 需兜底
        return create(kbId == null ? 0L : kbId, userId, t);
    }

    /** 校验会话归属：仅会话所有者可操作 */
    private ChatSession getOwnedSession(Long id, Long userId) {
        ChatSession session = getById(id);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作该会话");
        }
        return session;
    }
}
