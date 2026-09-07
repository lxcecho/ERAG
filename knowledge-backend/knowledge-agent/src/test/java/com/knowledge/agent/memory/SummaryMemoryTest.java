/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.memory;

import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.engine.LlmResult;
import com.knowledge.agent.memory.entity.AgentMemory;
import com.knowledge.agent.memory.mapper.AgentMemoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * {@link SummaryMemory} 单元测试：阈值判定 + summarize 调 LLM 持久化 + 空消息/异常降级。
 */
@ExtendWith(MockitoExtension.class)
class SummaryMemoryTest {

    @Mock
    private AgentLlmCaller llmCaller;
    @Mock
    private AgentMemoryMapper memoryMapper;
    @Mock
    private ConversationMemory conversationMemory;

    private SummaryMemory summaryMemory;

    private SummaryMemory build() {
        return new SummaryMemory(llmCaller, memoryMapper, conversationMemory, new MemoryProperties());
    }

    @Test
    void shouldSummarize_should_return_true_when_turns_reach_threshold() {
        summaryMemory = build();
        assertTrue(summaryMemory.shouldSummarize(10));
        assertTrue(summaryMemory.shouldSummarize(20));
    }

    @Test
    void shouldSummarize_should_return_false_when_turns_below_threshold() {
        summaryMemory = build();
        assertFalse(summaryMemory.shouldSummarize(9));
    }

    @Test
    void summarize_should_persist_summary_from_llm() {
        summaryMemory = build();
        List<ChatMessageVo> messages = List.of(msg("user", "问1"), msg("assistant", "答1"));
        when(conversationMemory.loadRecent(eq(100L), anyInt())).thenReturn(messages);
        when(llmCaller.call(anyString(), anyString(), eq("agent_memory"), any(LlmIdentity.class)))
                .thenReturn(new LlmResult("会话摘要文本", 50, 30, 80));

        String result = summaryMemory.summarize(100L, 10L, 1L);

        assertEquals("会话摘要文本", result);
        ArgumentCaptor<AgentMemory> captor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(memoryMapper).insert(captor.capture());
        AgentMemory saved = captor.getValue();
        assertEquals(100L, saved.getSessionId());
        assertEquals(10L, saved.getUserId());
        assertEquals(1L, saved.getTenantId());
        assertEquals(MemoryType.SUMMARY.name(), saved.getMemoryType());
        assertEquals("会话摘要文本", saved.getContent());
    }

    @Test
    void summarize_should_return_null_when_no_messages() {
        summaryMemory = build();
        when(conversationMemory.loadRecent(eq(100L), anyInt())).thenReturn(List.of());

        String result = summaryMemory.summarize(100L, 10L, 1L);

        assertNull(result);
        verify(llmCaller, never()).call(anyString(), anyString(), anyString(), any());
        verify(memoryMapper, never()).insert(any(AgentMemory.class));
    }

    @Test
    void summarize_should_return_null_when_llm_throws() {
        summaryMemory = build();
        when(conversationMemory.loadRecent(eq(100L), anyInt()))
                .thenReturn(List.of(msg("user", "问")));
        when(llmCaller.call(anyString(), anyString(), anyString(), any(LlmIdentity.class)))
                .thenThrow(new RuntimeException("LLM 超时"));

        String result = summaryMemory.summarize(100L, 10L, 1L);

        assertNull(result);
        verify(memoryMapper, never()).insert(any(AgentMemory.class));
    }

    @Test
    void loadSummary_should_return_latest_summary_content() {
        summaryMemory = build();
        AgentMemory latest = new AgentMemory();
        latest.setContent("最新摘要");
        when(memoryMapper.selectOne(any())).thenReturn(latest);

        String summary = summaryMemory.loadSummary(100L);

        assertEquals("最新摘要", summary);
    }

    @Test
    void loadSummary_should_return_null_when_absent() {
        summaryMemory = build();
        when(memoryMapper.selectOne(any())).thenReturn(null);

        assertNull(summaryMemory.loadSummary(100L));
    }

    private ChatMessageVo msg(String role, String content) {
        ChatMessageVo v = new ChatMessageVo();
        v.setRole(role);
        v.setContent(content);
        return v;
    }
}
