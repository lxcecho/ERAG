package com.knowledge.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.engine.LlmResult;
import com.knowledge.agent.memory.entity.AgentMemory;
import com.knowledge.agent.memory.mapper.AgentMemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话摘要记忆：用 LLM 压缩当前会话全量消息为摘要，存入 agent_memory(type=SUMMARY)。
 * <p>
 * 触发时机：会话轮次 ≥ {@link MemoryProperties#getSummaryThreshold()} 时由 MemoryService 异步调用
 * （阈值前不抽取，省 token）；同时提供手动 API（MemoryController）。
 * <p>
 * 摘要作用域：session（每会话一条最新摘要，loadSummary 取最新）。多租户：tenant_id 由拦截器自动注入。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryMemory {

    private static final String MODULE = "agent_memory";

    private static final String SYSTEM_PROMPT = """
            你是会话摘要助手。将给定的多轮对话压缩为简洁摘要，要求：
            1. 保留用户核心意图、关键事实与已确认结论
            2. 省略寒暄、重复与无关细节
            3. 控制在 300 字以内，用第三人称客观陈述
            直接输出摘要文本，不要附加任何解释性文字或标题。""";

    private final AgentLlmCaller llmCaller;
    private final AgentMemoryMapper memoryMapper;
    private final ConversationMemory conversationMemory;
    private final MemoryProperties props;

    /** 判断是否达到摘要阈值 */
    public boolean shouldSummarize(int turnCount) {
        return turnCount >= props.getSummaryThreshold();
    }

    /**
     * 生成会话摘要并持久化。
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param tenantId  租户ID
     * @return 摘要文本（LLM 生成），失败返回 null
     */
    public String summarize(Long sessionId, Long userId, Long tenantId) {
        List<ChatMessageVo> messages = conversationMemory.loadRecent(sessionId, Integer.MAX_VALUE);
        if (messages == null || messages.isEmpty()) {
            log.warn("[Memory:Summary] 会话无消息，跳过摘要 session={}", sessionId);
            return null;
        }

        String userPrompt = buildUserPrompt(messages);
        LlmResult result;
        try {
            result = llmCaller.call(SYSTEM_PROMPT, userPrompt, MODULE, LlmIdentity.of(userId, tenantId));
        } catch (Exception e) {
            log.warn("[Memory:Summary] LLM 摘要失败 session={} err={}", sessionId, e.getMessage());
            return null;
        }
        String summary = result.text();
        if (summary == null || summary.isBlank()) {
            log.warn("[Memory:Summary] LLM 返回空摘要 session={}", sessionId);
            return null;
        }

        AgentMemory entity = new AgentMemory();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setMemoryType(MemoryType.SUMMARY.name());
        entity.setContent(summary);
        memoryMapper.insert(entity);
        log.info("[Memory:Summary] 摘要已持久化 session={} id={} len={}",
                sessionId, entity.getId(), summary.length());
        return summary;
    }

    /** 加载会话最新摘要（无则返回 null） */
    public String loadSummary(Long sessionId) {
        AgentMemory latest = memoryMapper.selectOne(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getSessionId, sessionId)
                .eq(AgentMemory::getMemoryType, MemoryType.SUMMARY.name())
                .orderByDesc(AgentMemory::getId)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getContent();
    }

    private String buildUserPrompt(List<ChatMessageVo> messages) {
        StringBuilder sb = new StringBuilder("以下是用户与助手的对话记录，请生成摘要：\n\n");
        for (ChatMessageVo m : messages) {
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }
}
