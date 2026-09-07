/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.ai.chat.service.ChatMessageService;
import com.knowledge.common.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConversationMemory} 单元测试：Redis 命中/未命中回源、append 失效缓存、Redis 异常降级。
 * <p>需设置 {@link TenantContext}（key() 调用 requiredTenantId）。
 */
@ExtendWith(MockitoExtension.class)
class ConversationMemoryTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private ChatMessageService chatMessageService;

    private ConversationMemory memory;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        memory = new ConversationMemory(redis, chatMessageService, new MemoryProperties(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_return_from_redis_when_cache_hit() {
        // Redis 命中：直接返回末尾 window 条，不查 DB
        List<ChatMessageVo> cached = List.of(msg("user", "你好"), msg("assistant", "在的"), msg("user", "继续"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(serialize(cached));

        List<ChatMessageVo> result = memory.loadRecent(100L, 10);

        assertEquals(3, result.size());
        verify(chatMessageService, never()).listBySession(anyLong());
    }

    @Test
    void should_fallback_to_db_and_backfill_when_cache_miss() {
        // Redis 未命中 → 回源 DB → 回填缓存
        List<ChatMessageVo> db = List.of(msg("user", "问题1"), msg("assistant", "回答1"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(chatMessageService.listBySession(100L)).thenReturn(db);

        List<ChatMessageVo> result = memory.loadRecent(100L, 10);

        assertEquals(2, result.size());
        verify(chatMessageService).listBySession(100L);
        verify(valueOps).set(anyString(), anyString(), any());
    }

    @Test
    void should_tail_to_window_when_db_exceeds_limit() {
        // DB 返回 5 条，window=2 → 返回末尾 2 条
        List<ChatMessageVo> db = List.of(
                msg("user", "q1"), msg("assistant", "a1"),
                msg("user", "q2"), msg("assistant", "a2"),
                msg("user", "q3"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(chatMessageService.listBySession(100L)).thenReturn(db);

        List<ChatMessageVo> result = memory.loadRecent(100L, 2);

        assertEquals(2, result.size());
        // 末尾 2 条 = [a2, q3]
        assertEquals("a2", result.get(0).getContent());
        assertEquals("q3", result.get(1).getContent());
    }

    @Test
    void should_degrade_to_db_when_redis_read_throws() {
        // Redis 读异常 → 降级直通 DB（不阻断）
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(chatMessageService.listBySession(100L)).thenReturn(List.of(msg("user", "降级路径")));

        List<ChatMessageVo> result = memory.loadRecent(100L, 10);

        assertEquals(1, result.size());
        verify(chatMessageService).listBySession(100L);
    }

    @Test
    void append_should_save_messages_and_invalidate_cache() {
        memory.append(100L, "用户提问", "助手回答");

        verify(chatMessageService).saveUserMessage(100L, "用户提问");
        verify(chatMessageService).saveAssistantMessage(eq(100L), eq("助手回答"), any());
        // invalidate 失效缓存：redis.delete(key)
        verify(redis).delete(anyString());
    }

    @Test
    void append_should_skip_blank_messages() {
        memory.append(100L, "", null);

        verify(chatMessageService, never()).saveUserMessage(anyLong(), anyString());
        verify(chatMessageService, never()).saveAssistantMessage(anyLong(), anyString(), any());
    }

    @Test
    void should_return_empty_when_no_messages_anywhere() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(chatMessageService.listBySession(100L)).thenReturn(List.of());

        List<ChatMessageVo> result = memory.loadRecent(100L, 10);

        assertTrue(result.isEmpty());
    }

    // ==================== 辅助 ====================

    private ChatMessageVo msg(String role, String content) {
        ChatMessageVo v = new ChatMessageVo();
        v.setRole(role);
        v.setContent(content);
        return v;
    }

    private String serialize(List<ChatMessageVo> list) {
        try {
            return new ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
