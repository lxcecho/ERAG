/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.permission.service.DocPermissionService;
import com.knowledge.kb.service.KbDocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link DocumentTool} 单元测试：验证文档不存在/权限拒绝/字段裁剪/内容切片返回。
 */
@ExtendWith(MockitoExtension.class)
class DocumentToolTest {

    @Mock
    private KbDocumentService kbDocumentService;
    @Mock
    private MilvusService milvusService;
    @Mock
    private DocPermissionService docPermissionService;

    @Test
    void should_fail_when_document_not_found() {
        when(kbDocumentService.getById(99L)).thenReturn(null);

        DocumentTool tool = new DocumentTool(kbDocumentService, milvusService, docPermissionService);
        ToolResult result = tool.execute(newContext(), Map.of("documentId", 99));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("文档不存在"));
    }

    @Test
    void should_fail_when_no_permission() {
        when(kbDocumentService.getById(1L)).thenReturn(buildDoc());
        when(docPermissionService.filterDocIds(10L, 7L, Set.of(1L))).thenReturn(Set.of());

        DocumentTool tool = new DocumentTool(kbDocumentService, milvusService, docPermissionService);
        ToolResult result = tool.execute(newContext(), Map.of("documentId", 1));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("无权访问"));
    }

    @Test
    void should_return_metadata_and_content_for_all() {
        when(kbDocumentService.getById(1L)).thenReturn(buildDoc());
        when(docPermissionService.filterDocIds(10L, 7L, Set.of(1L))).thenReturn(Set.of(1L));
        when(milvusService.queryByDocument(1L, 5)).thenReturn(List.of(
                new RetrievalResult("片段1", 0.0, "doc.pdf", 1L, 0, "1_0", "vector"),
                new RetrievalResult("片段2", 0.0, "doc.pdf", 1L, 1, "1_1", "vector")
        ));

        DocumentTool tool = new DocumentTool(kbDocumentService, milvusService, docPermissionService);
        ToolResult result = tool.execute(newContext(), Map.of("documentId", 1, "fields", "all"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(1L, data.get("documentId"));
        assertTrue(data.containsKey("metadata"));
        assertTrue(data.containsKey("content"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
        assertEquals(2, content.size());
        assertEquals(0, content.get(0).get("chunkIndex"));
        assertEquals("片段1", content.get(0).get("content"));
    }

    @Test
    void should_return_metadata_only_when_fields_metadata() {
        when(kbDocumentService.getById(1L)).thenReturn(buildDoc());
        when(docPermissionService.filterDocIds(10L, 7L, Set.of(1L))).thenReturn(Set.of(1L));

        DocumentTool tool = new DocumentTool(kbDocumentService, milvusService, docPermissionService);
        ToolResult result = tool.execute(newContext(), Map.of("documentId", 1, "fields", "metadata"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(data.containsKey("metadata"));
        assertFalse(data.containsKey("content"));
    }

    @Test
    void should_return_empty_content_when_no_chunks() {
        when(kbDocumentService.getById(1L)).thenReturn(buildDoc());
        when(docPermissionService.filterDocIds(10L, 7L, Set.of(1L))).thenReturn(Set.of(1L));
        when(milvusService.queryByDocument(anyLong(), anyInt())).thenReturn(List.of());

        DocumentTool tool = new DocumentTool(kbDocumentService, milvusService, docPermissionService);
        ToolResult result = tool.execute(newContext(), Map.of("documentId", 1, "fields", "content"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertFalse(data.containsKey("metadata"));
        @SuppressWarnings("unchecked")
        List<?> content = (List<?>) data.get("content");
        assertTrue(content.isEmpty());
    }

    @Test
    void should_require_auth() {
        DocumentTool tool = new DocumentTool(kbDocumentService, milvusService, docPermissionService);
        assertTrue(tool.authRequired(), "文档读取涉及 KB 权限，需要权限校验");
    }

    @Test
    void should_have_expected_tool_name() {
        DocumentTool tool = new DocumentTool(kbDocumentService, milvusService, docPermissionService);
        assertEquals("document_read", tool.name());
    }

    // ==================== 测试辅助 ====================

    private static ToolContext newContext() {
        return ToolContext.builder()
                .tenantId(1L).userId(10L).kbId(7L).taskId(100L).stepId(null).build();
    }

    private static KbDocument buildDoc() {
        KbDocument doc = new KbDocument();
        doc.setId(1L);
        doc.setKbId(7L);
        doc.setOriginalName("doc.pdf");
        doc.setFileType("pdf");
        doc.setFileSuffix("pdf");
        doc.setFileSize(1024L);
        doc.setStatus(2);
        doc.setChunkCount(5);
        doc.setVersion(1);
        doc.setVisibility("P");
        return doc;
    }
}
