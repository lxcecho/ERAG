package com.knowledge.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.engine.LlmResult;
import com.knowledge.agent.memory.entity.AgentMemory;
import com.knowledge.agent.memory.mapper.AgentMemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 长期事实记忆：用 LLM 从会话抽取用户跨会话事实/偏好，逐条存入 agent_memory(type=LONG_TERM)，
 * 并同步索引至向量库供语义召回。
 * <p>
 * 与 {@link SummaryMemory} 的区别：
 * <ul>
 *   <li>作用域：LONG_TERM 归属 user（跨会话），SUMMARY 归属 session；</li>
 *   <li>粒度：LONG_TERM 为单条原子事实（"用户偏好 Python"），SUMMARY 为会话整体压缩；</li>
 *   <li>向量索引：LONG_TERM 每条索引（高频语义召回），SUMMARY 不索引（会话内已有 ConversationMemory）。</li>
 * </ul>
 * <p>
 * 触发时机：会话轮次 ≥ {@link MemoryProperties#getLongTermThreshold()} 时由 MemoryService 异步调用；
 * 同时提供手动 API（MemoryController）。每条事实携带 source_session_id 便于溯源。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemory {

    private static final String MODULE = "agent_memory";

    private static final String SYSTEM_PROMPT = """
            你是用户画像抽取助手。从对话中抽取用户的长期事实与偏好（跨会话稳定有效的信息），例如：
            - 身份背景（职业、技术栈、所在行业）
            - 偏好习惯（语言、工具、表达风格、内容深度）
            - 持久目标（正在做的项目、关注的方向）
            要求：
            1. 仅抽取明确或强暗示的事实，不臆测
            2. 每条为简短陈述（不超过 50 字）
            3. 无可抽取事实时返回空数组 []
            严格输出 JSON 数组，如 ["偏好 Java17","关注 RAG 架构"]，不要附加任何解释性文字。""";

    private final AgentLlmCaller llmCaller;
    private final AgentMemoryMapper memoryMapper;
    private final ConversationMemory conversationMemory;
    private final VectorMemory vectorMemory;
    private final MemoryProperties props;
    private final ObjectMapper objectMapper;

    /** 判断是否达到长期事实抽取阈值 */
    public boolean shouldExtract(int turnCount) {
        return turnCount >= props.getLongTermThreshold();
    }

    /**
     * 从会话抽取用户长期事实并持久化 + 向量索引。
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param tenantId  租户ID
     * @return 抽取的事实列表（已持久化），失败返回空列表
     */
    public List<String> extractFacts(Long sessionId, Long userId, Long tenantId) {
        List<ChatMessageVo> messages = conversationMemory.loadRecent(sessionId, Integer.MAX_VALUE);
        if (messages == null || messages.isEmpty()) {
            log.warn("[Memory:LongTerm] 会话无消息，跳过抽取 session={}", sessionId);
            return List.of();
        }

        String userPrompt = buildUserPrompt(messages);
        LlmResult result;
        try {
            result = llmCaller.call(SYSTEM_PROMPT, userPrompt, MODULE, LlmIdentity.of(userId, tenantId));
        } catch (Exception e) {
            log.warn("[Memory:LongTerm] LLM 抽取失败 session={} err={}", sessionId, e.getMessage());
            return List.of();
        }

        List<String> facts = parseFacts(result.text());
        if (facts.isEmpty()) {
            log.info("[Memory:LongTerm] 无可抽取事实 session={}", sessionId);
            return List.of();
        }

        List<String> persisted = new ArrayList<>(facts.size());
        for (String fact : facts) {
            if (fact == null || fact.isBlank()) {
                continue;
            }
            AgentMemory entity = new AgentMemory();
            entity.setTenantId(tenantId);
            entity.setUserId(userId);
            entity.setMemoryType(MemoryType.LONG_TERM.name());
            entity.setContent(fact);
            entity.setSourceSessionId(sessionId);
            memoryMapper.insert(entity);
            // 同步索引向量（best-effort，索引失败不阻断）
            vectorMemory.index(entity.getId(), fact, userId, MemoryType.LONG_TERM, sessionId);
            persisted.add(fact);
        }
        log.info("[Memory:LongTerm] 事实已持久化 session={} userId={} 条数={}",
                sessionId, userId, persisted.size());
        return persisted;
    }

    /**
     * 加载用户长期事实（带记忆衰减）。
     * <p>策略：按时间倒序取最近 N 条（由 maxFacts 控制），越新的事实优先级越高。
     * 旧事实不会被物理删除，但随着新事实积累会自然被挤出加载窗口，实现隐式衰减。
     *
     * @param userId 用户ID
     * @return 最近的长期事实列表
     */
    public List<String> loadFacts(Long userId) {
        int maxFacts = props.getMaxLongTermFacts();
        List<AgentMemory> list = memoryMapper.selectList(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getUserId, userId)
                .eq(AgentMemory::getMemoryType, MemoryType.LONG_TERM.name())
                .orderByDesc(AgentMemory::getId)
                .last("LIMIT " + maxFacts));
        return list.stream().map(AgentMemory::getContent).toList();
    }

    /**
     * 清理过期的长期事实记忆（定时任务调用）。
     * <p>删除超过 retentionDays 天的旧记忆，释放存储空间。
     *
     * @param retentionDays 保留天数
     * @return 清理的记录数
     */
    public int cleanupExpired(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(retentionDays);
        int deleted = memoryMapper.delete(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getMemoryType, MemoryType.LONG_TERM.name())
                .lt(AgentMemory::getCreateTime, cutoff));
        if (deleted > 0) {
            log.info("[Memory:LongTerm] 清理过期记忆 cutoff={} deleted={}", cutoff, deleted);
        }
        return deleted;
    }

    private String buildUserPrompt(List<ChatMessageVo> messages) {
        StringBuilder sb = new StringBuilder("以下是用户与助手的对话记录，请抽取用户长期事实：\n\n");
        for (ChatMessageVo m : messages) {
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    /** 防御式解析 LLM 输出为字符串列表（兼容 markdown 代码块包裹与纯数组） */
    private List<String> parseFacts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String json = stripCodeFence(raw).trim();
        try {
            List<String> facts = objectMapper.readValue(json, new TypeReference<>() {
            });
            return facts == null ? List.of() : facts;
        } catch (Exception e) {
            // 兜底：尝试截取首个 [ ] 之间内容再解析
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                try {
                    List<String> facts = objectMapper.readValue(json.substring(start, end + 1),
                            new TypeReference<>() {
                            });
                    return facts == null ? List.of() : facts;
                } catch (Exception ignore) {
                    // 解析彻底失败
                }
            }
            log.warn("[Memory:LongTerm] 事实 JSON 解析失败 raw.len={} err={}", raw.length(), e.getMessage());
            return List.of();
        }
    }

    private static String stripCodeFence(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            // 去掉首行 ```json 或 ```
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) {
                s = s.substring(0, lastFence);
            }
        }
        return s;
    }
}
