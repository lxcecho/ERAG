/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.memory;

import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.agent.memory.dto.MemoryContext;
import com.knowledge.agent.memory.dto.MemoryEntryVo;
import com.knowledge.agent.memory.dto.MemoryRecall;
import com.knowledge.agent.memory.entity.AgentMemory;
import com.knowledge.agent.memory.mapper.AgentMemoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryService} 单元测试：loadContext 装配 4 类记忆、recordInteraction 达阈值触发异步抽取、CRUD。
 * <p>memoryTaskPool 用「直接执行」ExecutorService（同步运行提交任务），便于验证异步分支。
 */
@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private ConversationMemory conversationMemory;
    @Mock
    private SummaryMemory summaryMemory;
    @Mock
    private LongTermMemory longTermMemory;
    @Mock
    private VectorMemory vectorMemory;
    @Mock
    private AgentMemoryMapper memoryMapper;

    private MemoryService memoryService;

    /** 直接执行线程池：submit 时同步运行，便于断言异步分支（真实实现，避免 Mockito 不必要桩） */
    private ExecutorService directExecutor() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override
            public void shutdown() {
            }

            @Override
            public java.util.List<Runnable> shutdownNow() {
                return java.util.List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
                return true;
            }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    private MemoryService build(int threshold) {
        MemoryProperties props = new MemoryProperties();
        props.setSummaryThreshold(threshold);
        props.setLongTermThreshold(threshold);
        return new MemoryService(conversationMemory, summaryMemory, longTermMemory,
                vectorMemory, memoryMapper, props, directExecutor());
    }

    @BeforeEach
    void setUp() {
        memoryService = build(2);
    }

    @Test
    void loadContext_should_assemble_all_four_memories() {
        when(conversationMemory.loadRecent(eq(100L), anyInt()))
                .thenReturn(List.of(msg("user", "近期问题")));
        when(summaryMemory.loadSummary(100L)).thenReturn("会话摘要");
        when(longTermMemory.loadFacts(10L)).thenReturn(List.of("偏好 Java17"));
        when(vectorMemory.recall(anyString(), eq(10L), anyInt()))
                .thenReturn(List.of(new MemoryRecall("历史事实", 0.9, "LONG_TERM", 99L)));

        MemoryContext ctx = memoryService.loadContext(100L, 10L, 1L, "用户目标");

        assertEquals(1, ctx.getRecentMessages().size());
        assertEquals("会话摘要", ctx.getSummary());
        assertEquals(1, ctx.getLongTermFacts().size());
        assertEquals(1, ctx.getVectorRecalls().size());
        assertFalse(ctx.isEmpty());
        assertTrue(ctx.toPromptText().contains("会话摘要"));
        assertTrue(ctx.toPromptText().contains("偏好 Java17"));
    }

    @Test
    void loadContext_should_degrade_when_sub_memory_throws() {
        // 某子记忆抛异常 → 降级为空，不影响其他 + 不抛出
        when(conversationMemory.loadRecent(eq(100L), anyInt())).thenThrow(new RuntimeException("redis down"));
        when(summaryMemory.loadSummary(100L)).thenReturn("摘要");

        MemoryContext ctx = memoryService.loadContext(100L, 10L, 1L, "目标");

        assertTrue(ctx.getRecentMessages() == null || ctx.getRecentMessages().isEmpty());
        assertEquals("摘要", ctx.getSummary());
    }

    @Test
    void recordInteraction_should_trigger_summary_and_extract_when_turns_reach_threshold() {
        // 2 条 user 消息 = 2 轮，达到阈值 2（2%2==0）→ 触发抽取
        when(conversationMemory.loadRecent(eq(100L), anyInt()))
                .thenReturn(List.of(msg("user", "q1"), msg("assistant", "a1"), msg("user", "q2")));

        memoryService.recordInteraction(100L, 10L, 1L, "q2", "a2");

        verify(conversationMemory).append(100L, "q2", "a2");
        verify(summaryMemory).summarize(100L, 10L, 1L);
        verify(longTermMemory).extractFacts(100L, 10L, 1L);
    }

    @Test
    void recordInteraction_should_not_trigger_when_turns_below_threshold() {
        // 1 条 user = 1 轮，未达阈值 2
        when(conversationMemory.loadRecent(eq(100L), anyInt()))
                .thenReturn(List.of(msg("user", "q1")));

        memoryService.recordInteraction(100L, 10L, 1L, "q1", "a1");

        verify(conversationMemory).append(100L, "q1", "a1");
        verify(summaryMemory, never()).summarize(anyLong(), anyLong(), anyLong());
        verify(longTermMemory, never()).extractFacts(anyLong(), anyLong(), anyLong());
    }

    @Test
    void listMemories_should_return_vos_filtered_by_type() {
        AgentMemory m = new AgentMemory();
        m.setId(1L);
        m.setUserId(10L);
        m.setMemoryType(MemoryType.LONG_TERM.name());
        m.setContent("偏好 Python");
        when(memoryMapper.selectList(any())).thenReturn(List.of(m));

        List<MemoryEntryVo> vos = memoryService.listMemories(10L, "LONG_TERM");

        assertEquals(1, vos.size());
        assertEquals("偏好 Python", vos.get(0).getContent());
        assertEquals(MemoryType.LONG_TERM.name(), vos.get(0).getMemoryType());
    }

    @Test
    void deleteMemory_should_delete_db_and_vector() {
        AgentMemory m = new AgentMemory();
        m.setId(1L);
        m.setMemoryType(MemoryType.LONG_TERM.name());
        when(memoryMapper.selectById(1L)).thenReturn(m);

        memoryService.deleteMemory(1L);

        verify(memoryMapper).deleteById(1L);
        verify(vectorMemory).removeByMemoryId(1L);
    }

    @Test
    void deleteMemory_should_noop_when_absent() {
        when(memoryMapper.selectById(1L)).thenReturn(null);

        memoryService.deleteMemory(1L);

        verify(memoryMapper, never()).deleteById(anyLong());
        verify(vectorMemory, never()).removeByMemoryId(any());
    }

    private ChatMessageVo msg(String role, String content) {
        ChatMessageVo v = new ChatMessageVo();
        v.setRole(role);
        v.setContent(content);
        return v;
    }
}
