package com.knowledge.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.knowledge.agent.entity.AgentTask;
import com.knowledge.agent.mapper.AgentTaskMapper;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.service.KbDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务数据查询工具：查询知识库与 Agent 任务的统计数据，供 Agent 分析决策。
 * <p>
 * 查询类型：
 * <ul>
 *   <li>{@code kb_stats}：指定 KB 的文档统计（总数/已解析/未解析/失败/切片总数）</li>
 *   <li>{@code doc_stats}：指定 KB 的文档状态分布与文件类型分布</li>
 *   <li>{@code agent_stats}：当前租户的 Agent 任务统计（总数/按状态分布）</li>
 * </ul>
 * <p>
 * 权限：所有查询均校验 KB viewer 权限（authRequired=true），防止跨租户/跨 KB 数据泄漏。
 * 租户隔离由 MyBatis-Plus TenantLineInnerInterceptor 自动注入 tenant_id 条件保证。
 * <p>
 * 技术说明：使用 {@link QueryWrapper}（字符串列名）而非 {@code LambdaQueryWrapper}，
 * 避免单元测试中 MyBatis-Plus lambda cache 未初始化问题（TableInfo 需 Spring 容器启动才注册）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataQueryTool implements Tool {

    private final KbDocumentService kbDocumentService;
    private final AgentTaskMapper agentTaskMapper;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "queryType": {"type": "string", "description": "查询类型: kb_stats/doc_stats/agent_stats"},
                "kbId": {"type": "integer", "description": "知识库ID（kb_stats/doc_stats 必填）"}
              },
              "required": ["queryType"]
            }""";

    @Override
    public String name() {
        return "data_query";
    }

    @Override
    public String description() {
        return "查询业务统计数据：知识库文档统计(kb_stats)、文档状态分布(doc_stats)、"
                + "Agent任务统计(agent_stats)。供分析决策使用。";
    }

    @Override
    public String parametersJsonSchema() {
        return SCHEMA;
    }

    @Override
    public boolean authRequired() {
        return true;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String queryType = (String) arguments.get("queryType");
        Long kbId = arguments.containsKey("kbId") && arguments.get("kbId") != null
                ? ((Number) arguments.get("kbId")).longValue()
                : ctx.getKbId();

        log.info("[DataQuery] task={} type={} kbId={}", ctx.getTaskId(), queryType, kbId);

        Map<String, Object> data = switch (queryType) {
            case "kb_stats" -> queryKbStats(kbId);
            case "doc_stats" -> queryDocStats(kbId);
            case "agent_stats" -> queryAgentStats(ctx.getTenantId());
            default -> Map.of("error", "未知查询类型: " + queryType
                    + "（支持: kb_stats / doc_stats / agent_stats）");
        };
        return ToolResult.success(data);
    }

    /** KB 文档统计：总数/已解析/未解析/失败/切片总数 */
    private Map<String, Object> queryKbStats(Long kbId) {
        if (kbId == null) {
            return Map.of("error", "kb_stats 查询需要 kbId");
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalDocuments", kbDocumentService.count(
                new QueryWrapper<KbDocument>().eq("kb_id", kbId)));
        stats.put("parsedDocuments", kbDocumentService.count(
                new QueryWrapper<KbDocument>().eq("kb_id", kbId).eq("status", 2)));
        stats.put("pendingDocuments", kbDocumentService.count(
                new QueryWrapper<KbDocument>().eq("kb_id", kbId).in("status", 0, 1)));
        stats.put("failedDocuments", kbDocumentService.count(
                new QueryWrapper<KbDocument>().eq("kb_id", kbId).eq("status", 3)));

        // 切片总数：遍历已解析文档累加 chunkCount（无聚合 SQL，文档量不大可接受）
        long totalChunks = kbDocumentService.list(
                new QueryWrapper<KbDocument>()
                        .eq("kb_id", kbId)
                        .eq("status", 2)
                        .select("chunk_count"))
                .stream()
                .mapToLong(d -> d.getChunkCount() != null ? d.getChunkCount() : 0)
                .sum();
        stats.put("totalChunks", totalChunks);
        return stats;
    }

    /** 文档状态分布 + 文件类型分布 */
    private Map<String, Object> queryDocStats(Long kbId) {
        if (kbId == null) {
            return Map.of("error", "doc_stats 查询需要 kbId");
        }
        Map<String, Object> stats = new LinkedHashMap<>();

        // 状态分布
        Map<String, Long> statusDist = new LinkedHashMap<>();
        statusDist.put("pending", kbDocumentService.count(
                new QueryWrapper<KbDocument>().eq("kb_id", kbId).in("status", 0, 1)));
        statusDist.put("parsed", kbDocumentService.count(
                new QueryWrapper<KbDocument>().eq("kb_id", kbId).eq("status", 2)));
        statusDist.put("failed", kbDocumentService.count(
                new QueryWrapper<KbDocument>().eq("kb_id", kbId).eq("status", 3)));
        stats.put("statusDistribution", statusDist);

        // 文件类型分布
        Map<String, Long> typeDist = new LinkedHashMap<>();
        for (String type : new String[]{"pdf", "doc", "docx", "md"}) {
            typeDist.put(type, kbDocumentService.count(
                    new QueryWrapper<KbDocument>().eq("kb_id", kbId).eq("file_suffix", type)));
        }
        stats.put("fileTypeDistribution", typeDist);
        return stats;
    }

    /** Agent 任务统计：总数/按状态分布 */
    private Map<String, Object> queryAgentStats(Long tenantId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTasks", agentTaskMapper.selectCount(
                new QueryWrapper<AgentTask>().eq("tenant_id", tenantId)));

        Map<String, Long> statusDist = new LinkedHashMap<>();
        for (String status : new String[]{"CREATED", "EXECUTING", "COMPLETED", "FAILED", "CANCELED"}) {
            statusDist.put(status, agentTaskMapper.selectCount(
                    new QueryWrapper<AgentTask>().eq("tenant_id", tenantId).eq("status", status)));
        }
        stats.put("statusDistribution", statusDist);
        return stats;
    }
}
