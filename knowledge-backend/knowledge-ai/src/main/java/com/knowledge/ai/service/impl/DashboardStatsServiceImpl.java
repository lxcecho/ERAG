package com.knowledge.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.knowledge.ai.chat.entity.ChatMessage;
import com.knowledge.ai.chat.mapper.ChatMessageMapper;
import com.knowledge.ai.dto.DashboardStatsVo;
import com.knowledge.ai.service.DashboardStatsService;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.mapper.KbDocumentMapper;
import com.knowledge.kb.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 工作台统计实现：直接基于 Mapper 聚合查询，软删过滤与租户隔离由 MyBatis-Plus 拦截器自动处理。
 *
 * @author: lxcechoo@gmail.com
 */
@Service
@RequiredArgsConstructor
public class DashboardStatsServiceImpl implements DashboardStatsService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final ChatMessageMapper chatMessageMapper;

    @Override
    public DashboardStatsVo stats() {
        Long kbCount = knowledgeBaseMapper.selectCount(null);
        Long docCount = kbDocumentMapper.selectCount(null);
        long chunkCount = sumChunks();
        Long todayChatCount = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRole, "user")
                .ge(ChatMessage::getCreateTime, LocalDate.now().atStartOfDay()));
        return new DashboardStatsVo(kbCount, docCount, chunkCount, todayChatCount);
    }

    /** 切片总数 = SUM(kb_document.chunk_count)，软删文档不计 */
    private long sumChunks() {
        return kbDocumentMapper.selectMaps(new QueryWrapper<KbDocument>()
                        .select("IFNULL(SUM(chunk_count), 0) AS total")
                        .eq("deleted", 0))
                .stream().findFirst()
                .map(m -> ((Number) m.get("total")).longValue())
                .orElse(0L);
    }
}
