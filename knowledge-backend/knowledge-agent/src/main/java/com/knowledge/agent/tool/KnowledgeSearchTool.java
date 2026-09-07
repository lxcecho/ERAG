package com.knowledge.agent.tool;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.dto.Evidence;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.search.SearchService;
import com.knowledge.ai.service.EmbeddingService;
import com.knowledge.kb.permission.service.DocPermissionService;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 知识检索工具：Agent 层与 RAG 层的桥接点。
 * <p>
 * 核心职责：把"用户查询 → 权限安全的检索结果"封装成一个对 Agent 透明的调用。
 * <p>
 * <b>权限安全（关键）</b>：复用已有 {@link DocPermissionService#filterDocIds} 做检索后过滤，
 * 与 {@code RagServiceImpl} 的 RAG Post-Filter 走完全相同的权限链路——
 * Agent 检索与人工 RAG 检索权限模型完全一致，无法越权。
 * <p>
 * <b>解耦</b>：本类是 Agent 模块中唯一感知 LangChain4j 类型（{@link Embedding}）的地方，
 * 将 {@link RetrievalResult}（RAG 层 DTO）转换为 {@link Evidence}（Agent 层 DTO）；
 * 4 个 Agent 只依赖 {@code Evidence}，不感知 RAG 层数据结构。
 * <p>
 * <b>双接口</b>：同时实现 Tool SPI（供 {@link ToolExecutor} 统一调度）+ 保留 {@link #search}
 * 方法（供 {@code KnowledgeAgent} 直接调用，向后兼容）。
 *
 * @see com.knowledge.kb.permission.service.DocPermissionService
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool implements Tool {

    private final EmbeddingService embeddingService;
    private final SearchService searchService;
    private final DocPermissionService docPermissionService;
    private final AgentProperties props;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {"type": "string", "description": "检索查询（自然语言关键词）"},
                "topK": {"type": "integer", "description": "返回证据条数", "default": 8}
              },
              "required": ["query"]
            }""";

    // ==================== Tool SPI 实现 ====================

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String description() {
        return "在企业知识库中执行混合检索（向量+关键词），返回权限过滤后的证据片段。"
                + "适用于按关键词查找文档内容、政策条款、技术规范等。";
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
        String query = (String) arguments.get("query");
        int topK = arguments.containsKey("topK") && arguments.get("topK") != null
                ? ((Number) arguments.get("topK")).intValue()
                : props.getSearchTopK();

        List<Evidence> evidences = search(query, ctx.getKbId(), ctx.getUserId(), topK);
        if (evidences.isEmpty()) {
            return ToolResult.failure("未检索到任何资料（可能无权限或知识库为空）");
        }
        return ToolResult.success(evidences);
    }

    // ==================== 直接调用 API（KnowledgeAgent 向后兼容） ====================

    /**
     * 权限安全的混合检索。
     *
     * @param query 检索查询（Planner 拆解出的关键词）
     * @param kbId  知识库ID
     * @param userId 当前用户ID（权限判定主体）
     * @param topK  最终返回证据条数
     * @return 经过权限过滤的证据列表（可能为空）
     */
    public List<Evidence> search(String query, Long kbId, Long userId, int topK) {
        // 1. 向量化查询
        Embedding queryEmbedding = embeddingService.embed(query);

        // 2. 初召放大：给权限 Post-Filter 留余量（避免过滤后不足 topK）
        int initTopK = Math.max(topK * props.getRetrieval().getOverFetchMultiplier(),
                props.getRetrieval().getMinOverFetch());
        List<RetrievalResult> results = searchService.hybridSearch(query, queryEmbedding, kbId, initTopK);

        // 3. 权限后过滤：取命中 docId 集合，交由 DocPermissionService 裁剪为用户可见子集
        List<Long> hitDocIds = results.stream()
                .map(RetrievalResult::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<Long> allowedDocIds = docPermissionService.filterDocIds(userId, kbId, hitDocIds);

        // 4. 按 docId 过滤 chunk + 截断到 topK
        List<Evidence> evidences = results.stream()
                .filter(r -> r.getDocumentId() != null && allowedDocIds.contains(r.getDocumentId()))
                .limit(topK)
                .map(KnowledgeSearchTool::toEvidence)
                .toList();

        log.debug("KnowledgeSearch 查询=[{}] 初召={} 权限通过doc={} 最终证据={}",
                query, results.size(), allowedDocIds.size(), evidences.size());
        return evidences;
    }

    /** RetrievalResult → Evidence（领域转换，隔离 RAG 层数据结构） */
    private static Evidence toEvidence(RetrievalResult r) {
        Evidence e = new Evidence();
        e.setDocumentId(r.getDocumentId());
        e.setSource(r.getSource());
        e.setChunkId(r.getChunkId());
        e.setChunkIndex(r.getChunkIndex());
        e.setContent(r.getText());
        e.setScore(r.getScore());
        e.setScoreType(r.getScoreType());
        return e;
    }
}
