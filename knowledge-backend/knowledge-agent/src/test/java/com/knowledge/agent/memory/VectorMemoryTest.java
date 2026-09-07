/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.memory;

import com.knowledge.ai.service.EmbeddingService;
import com.knowledge.agent.memory.dto.MemoryRecall;
import com.knowledge.common.context.TenantContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VectorMemory} 单元测试：index 调 embed+store、recall 按 tenant+user 过滤召回、空/异常降级。
 * <p>需设置 {@link TenantContext}（index/recall 调 requiredTenantId）。
 */
@ExtendWith(MockitoExtension.class)
class VectorMemoryTest {

    @Mock
    private EmbeddingStore<TextSegment> memoryEmbeddingStore;
    @Mock
    private EmbeddingService embeddingService;

    private VectorMemory vectorMemory;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        vectorMemory = new VectorMemory(memoryEmbeddingStore, embeddingService, new MemoryProperties());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void index_should_embed_and_store_with_metadata() {
        when(embeddingService.embed(anyString())).thenReturn(new Embedding(new float[]{0.1f, 0.2f}));

        vectorMemory.index(500L, "用户偏好 Java17", 10L, MemoryType.LONG_TERM, 100L);

        verify(embeddingService).embed("用户偏好 Java17");
        verify(memoryEmbeddingStore).add(any(Embedding.class), any(TextSegment.class));
    }

    @Test
    void index_should_skip_blank_text() {
        vectorMemory.index(500L, "  ", 10L, MemoryType.SUMMARY, null);

        verify(embeddingService, never()).embed(anyString());
        verify(memoryEmbeddingStore, never()).add(any(Embedding.class), any(TextSegment.class));
    }

    @Test
    void index_should_not_throw_when_embed_fails() {
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("embedding down"));

        // best-effort：索引失败不抛
        vectorMemory.index(500L, "事实", 10L, MemoryType.LONG_TERM, 100L);

        verify(memoryEmbeddingStore, never()).add(any(Embedding.class), any(TextSegment.class));
    }

    @Test
    void recall_should_return_matches_filtered_by_tenant_user() {
        when(embeddingService.embed(anyString())).thenReturn(new Embedding(new float[]{0.1f}));
        Metadata md = new Metadata()
                .put(VectorMemory.FIELD_TENANT_ID, "1")
                .put(VectorMemory.FIELD_USER_ID, "10")
                .put(VectorMemory.FIELD_MEMORY_TYPE, MemoryType.LONG_TERM.name())
                .put(VectorMemory.FIELD_MEMORY_ID, "500")
                .put(VectorMemory.FIELD_SOURCE_SESSION_ID, "100");
        TextSegment segment = TextSegment.from("用户偏好 Java17", md);
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.9, "id-1", new Embedding(new float[]{0.1f}), segment);
        when(memoryEmbeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(match)));

        List<MemoryRecall> recalls = vectorMemory.recall("用户技术栈", 10L, 5);

        assertEquals(1, recalls.size());
        MemoryRecall r = recalls.get(0);
        assertEquals("用户偏好 Java17", r.getContent());
        assertEquals(0.9, r.getScore());
        assertEquals(MemoryType.LONG_TERM.name(), r.getMemoryType());
        assertEquals(100L, r.getSourceSessionId());
    }

    @Test
    void recall_should_return_empty_when_query_blank() {
        List<MemoryRecall> recalls = vectorMemory.recall("  ", 10L, 5);

        assertTrue(recalls.isEmpty());
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void recall_should_return_empty_when_search_throws() {
        when(embeddingService.embed(anyString())).thenReturn(new Embedding(new float[]{0.1f}));
        when(memoryEmbeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenThrow(new RuntimeException("milvus down"));

        List<MemoryRecall> recalls = vectorMemory.recall("查询", 10L, 5);

        assertTrue(recalls.isEmpty());
    }

    @Test
    void removeByMemoryId_should_remove_with_tenant_filter() {
        vectorMemory.removeByMemoryId(500L);

        verify(memoryEmbeddingStore).removeAll(any(dev.langchain4j.store.embedding.filter.Filter.class));
    }
}
