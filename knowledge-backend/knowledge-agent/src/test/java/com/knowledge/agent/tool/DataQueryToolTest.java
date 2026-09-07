/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.agent.mapper.AgentTaskMapper;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.service.KbDocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link DataQueryTool} 单元测试：验证三种查询类型的统计结果结构。
 */
@ExtendWith(MockitoExtension.class)
class DataQueryToolTest {

    @Mock
    private KbDocumentService kbDocumentService;
    @Mock
    private AgentTaskMapper agentTaskMapper;

    @Test
    void should_return_kb_stats_with_expected_keys() {
        // 文档数 5，已解析的文档含 10 个切片
        when(kbDocumentService.count(any(Wrapper.class))).thenReturn(5L);
        KbDocument doc = new KbDocument();
        doc.setChunkCount(10);
        when(kbDocumentService.list(any(Wrapper.class))).thenReturn(List.of(doc));

        DataQueryTool tool = new DataQueryTool(kbDocumentService, agentTaskMapper);
        ToolResult result = tool.execute(newContext(100L), Map.of("queryType", "kb_stats"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(5L, data.get("totalDocuments"));
        assertEquals(5L, data.get("parsedDocuments"));
        assertEquals(5L, data.get("pendingDocuments"));
        assertEquals(5L, data.get("failedDocuments"));
        assertEquals(10L, data.get("totalChunks"));
    }

    @Test
    void should_return_agent_stats_with_status_distribution() {
        when(agentTaskMapper.selectCount(any(Wrapper.class))).thenReturn(3L);

        DataQueryTool tool = new DataQueryTool(kbDocumentService, agentTaskMapper);
        ToolResult result = tool.execute(newContext(100L), Map.of("queryType", "agent_stats"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(3L, data.get("totalTasks"));
        @SuppressWarnings("unchecked")
        Map<String, Long> statusDist = (Map<String, Long>) data.get("statusDistribution");
        assertEquals(3L, statusDist.get("COMPLETED"));
        assertEquals(3L, statusDist.get("FAILED"));
    }

    @Test
    void should_return_error_for_unknown_query_type() {
        DataQueryTool tool = new DataQueryTool(kbDocumentService, agentTaskMapper);
        ToolResult result = tool.execute(newContext(100L), Map.of("queryType", "unknown_type"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(data.containsKey("error"));
    }

    @Test
    void should_require_auth() {
        DataQueryTool tool = new DataQueryTool(kbDocumentService, agentTaskMapper);
        assertTrue(tool.authRequired(), "数据查询工具访问 KB 数据，需要权限校验");
    }

    // ==================== 测试辅助 ====================

    private static ToolContext newContext(Long kbId) {
        AgentTask task = new AgentTask();
        task.setId(1L);
        task.setTenantId(1L);
        task.setUserId(10L);
        task.setKbId(kbId);
        return ToolContext.of(task, null);
    }
}
