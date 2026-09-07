/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.dto.Evidence;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.search.SearchService;
import com.knowledge.ai.service.EmbeddingService;
import com.knowledge.kb.permission.service.DocPermissionService;
import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeSearchTool} 单元测试：验证权限后过滤（安全核心）。
 * <p>场景：检索召回 3 个文档，其中文档2 无权限 → 工具只返回有权限的证据。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSearchToolTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private SearchService searchService;
    @Mock
    private DocPermissionService docPermissionService;

    private KnowledgeSearchTool tool;

    @BeforeEach
    void setUp() {
        AgentProperties props = new AgentProperties();
        tool = new KnowledgeSearchTool(embeddingService, searchService, docPermissionService, props);
    }

    @Test
    void should_filter_out_unauthorized_documents() {
        // given：初召 3 个 chunk，分属 doc1/doc2/doc3
        List<RetrievalResult> initial = List.of(
                new RetrievalResult("2025返利政策", 0.9, "2025.pdf", 1L, 0, "doc1_0", "fused"),
                new RetrievalResult("2024返利政策", 0.85, "2024.pdf", 2L, 0, "doc2_0", "fused"),
                new RetrievalResult("区域政策", 0.8, "区域.pdf", 3L, 0, "doc3_0", "fused")
        );
        when(embeddingService.embed(any())).thenReturn(new Embedding(new float[]{0.1f}));
        when(searchService.hybridSearch(anyString(), any(Embedding.class), anyLong(), anyInt())).thenReturn(initial);
        // 权限：doc2 被拒（用户无 VIEW 权限），doc1/doc3 放行
        when(docPermissionService.filterDocIds(anyLong(), anyLong(), any()))
                .thenReturn(Set.of(1L, 3L));

        // when
        List<Evidence> evidences = tool.search("返利政策", 100L, 1L, 5);

        // then：只剩 doc1、doc3 的证据
        assertEquals(2, evidences.size());
        Set<Long> allowedDocs = evidences.stream().map(Evidence::getDocumentId).collect(java.util.stream.Collectors.toSet());
        assertTrue(allowedDocs.contains(1L));
        assertTrue(allowedDocs.contains(3L));
        assertTrue(!allowedDocs.contains(2L), "无权限的 doc2 必须被过滤");
    }

    @Test
    void should_return_empty_when_all_denied() {
        List<RetrievalResult> initial = List.of(
                new RetrievalResult("secret", 0.9, "secret.pdf", 5L, 0, "doc5_0", "fused")
        );
        when(embeddingService.embed(any())).thenReturn(new Embedding(new float[]{0.1f}));
        when(searchService.hybridSearch(anyString(), any(Embedding.class), anyLong(), anyInt())).thenReturn(initial);
        // 全部拒绝
        when(docPermissionService.filterDocIds(anyLong(), anyLong(), any())).thenReturn(Set.of());

        List<Evidence> evidences = tool.search("secret", 100L, 1L, 5);

        assertTrue(evidences.isEmpty(), "全部无权限时应返回空");
    }

    @Test
    void should_truncate_to_topK() {
        // given：6 条全部有权限，topK=3 → 只返回 3 条
        List<RetrievalResult> initial = List.of(
                new RetrievalResult("t1", 0.9, "a.pdf", 1L, 0, "doc1_0", "fused"),
                new RetrievalResult("t2", 0.88, "b.pdf", 1L, 1, "doc1_1", "fused"),
                new RetrievalResult("t3", 0.86, "c.pdf", 1L, 2, "doc1_2", "fused"),
                new RetrievalResult("t4", 0.84, "d.pdf", 1L, 3, "doc1_3", "fused"),
                new RetrievalResult("t5", 0.82, "e.pdf", 1L, 4, "doc1_4", "fused"),
                new RetrievalResult("t6", 0.80, "f.pdf", 1L, 5, "doc1_5", "fused")
        );
        when(embeddingService.embed(any())).thenReturn(new Embedding(new float[]{0.1f}));
        when(searchService.hybridSearch(anyString(), any(Embedding.class), anyLong(), anyInt())).thenReturn(initial);
        when(docPermissionService.filterDocIds(anyLong(), anyLong(), any())).thenReturn(Set.of(1L));

        List<Evidence> evidences = tool.search("query", 100L, 1L, 3);

        assertEquals(3, evidences.size(), "应截断到 topK");
    }
}
