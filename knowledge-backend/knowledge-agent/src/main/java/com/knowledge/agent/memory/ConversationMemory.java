package com.knowledge.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.ai.chat.service.ChatMessageService;
import com.knowledge.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 会话记忆：当前会话最近 N 轮消息，Redis 热缓存 + ChatMessageService 回源。
 * <p>
 * 缓存策略：loadRecent 读 Redis（命中则取末尾 window 条），未命中回源 DB 并回填缓存（TTL）；
 * append 写 DB 后失效缓存（delete），保证一致性（写后下一次 load 重新回源回填）。
 * <p>
 * best-effort：Redis 异常静默降级直通 DB，不阻断主流程。租户隔离：Redis key 含 tenantId。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemory {

    private static final String KEY_PREFIX = "erag:memory:conv:";

    private final StringRedisTemplate redis;
    private final ChatMessageService chatMessageService;
    private final MemoryProperties props;
    private final ObjectMapper objectMapper;

    /**
     * 加载会话近期消息（按时间正序，最多 window 条）。
     *
     * @param sessionId 会话ID
     * @param window    返回条数上限（<=0 用配置默认）
     * @return 近期消息（正序），无则空列表
     */
    public List<ChatMessageVo> loadRecent(Long sessionId, int window) {
        int limit = window > 0 ? window : props.getConversationWindow();
        String key = key(sessionId);
        // Redis 优先
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                List<ChatMessageVo> all = objectMapper.readValue(cached, new TypeReference<>() {
                });
                return tail(all, limit);
            }
        } catch (Exception e) {
            log.warn("[Memory:Conv] Redis 读取降级直通 DB session={} err={}", sessionId, e.getMessage());
        }
        // 回源 DB
        List<ChatMessageVo> all = chatMessageService.listBySession(sessionId);
        // 回填缓存（best-effort）
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(all),
                    Duration.ofSeconds(props.getConvCacheTtlSeconds()));
        } catch (Exception e) {
            log.warn("[Memory:Conv] Redis 回填失败 session={} err={}", sessionId, e.getMessage());
        }
        return tail(all, limit);
    }

    /**
     * 追加一轮对话（user + assistant）：写 DB + 失效缓存。
     *
     * @param sessionId     会话ID
     * @param userMsg       用户消息
     * @param assistantMsg  助手消息（可空，仅 user 时跳过 assistant）
     */
    public void append(Long sessionId, String userMsg, String assistantMsg) {
        if (userMsg != null && !userMsg.isBlank()) {
            chatMessageService.saveUserMessage(sessionId, userMsg);
        }
        if (assistantMsg != null && !assistantMsg.isBlank()) {
            chatMessageService.saveAssistantMessage(sessionId, assistantMsg, null);
        }
        invalidate(sessionId);
    }

    /** 失效会话缓存（写后调用，保证下次 load 回源最新数据） */
    public void invalidate(Long sessionId) {
        try {
            redis.delete(key(sessionId));
        } catch (Exception e) {
            log.warn("[Memory:Conv] Redis 失效失败 session={} err={}", sessionId, e.getMessage());
        }
    }

    private String key(Long sessionId) {
        return KEY_PREFIX + TenantContext.requiredTenantId() + ":" + sessionId;
    }

    /** 取列表末尾 n 条（保持正序） */
    private static <T> List<T> tail(List<T> list, int n) {
        if (list == null || list.isEmpty() || n <= 0) {
            return List.of();
        }
        if (list.size() <= n) {
            return list;
        }
        return list.subList(list.size() - n, list.size());
    }
}
