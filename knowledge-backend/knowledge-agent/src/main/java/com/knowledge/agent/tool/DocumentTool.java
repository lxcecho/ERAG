package com.knowledge.agent.tool;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.permission.service.DocPermissionService;
import com.knowledge.kb.service.KbDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档读取工具：按文档ID获取文档元数据与内容切片，供 Agent 精确引用具体文档。
 * <p>
 * 与 {@link KnowledgeSearchTool} 的区别：
 * <ul>
 *   <li>{@code knowledge_search}：语义检索，按查询相关性召回多文档的片段；</li>
 *   <li>{@code document_read}：精确读取，按 documentId 取单文档的元数据 + 原文切片（按 chunkIndex 顺序）。</li>
 * </ul>
 * <p>
 * <b>权限安全</b>：复用 {@link DocPermissionService#filterDocIds} 校验用户对该文档的 VIEW 权限，
 * 与 RAG 检索后过滤走同一权限链路，无法越权读取。
 * <p>
 * <b>内容来源</b>：切片存于 Milvus 向量库，通过 {@link MilvusService#queryByDocument} 按文档ID过滤读取
 * （非语义检索，按 chunkIndex 升序返回原文）。文档未解析或无切片时 content 为空数组，元数据仍正常返回。
 * <p>
 * <b>fields 控制</b>：{@code metadata}=仅元数据 / {@code content}=仅切片 / {@code all}=两者（默认）。
 *
 * @see KnowledgeSearchTool
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTool implements Tool {

    private final KbDocumentService kbDocumentService;
    private final MilvusService milvusService;
    private final DocPermissionService docPermissionService;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "documentId": {"type": "integer", "description": "文档ID"},
                "fields": {"type": "string", "description": "返回字段 metadata/content/all", "default": "all"},
                "maxChunks": {"type": "integer", "description": "内容切片最大条数", "default": 5}
              },
              "required": ["documentId"]
            }""";

    @Override
    public String name() {
        return "document_read";
    }

    @Override
    public String description() {
        return "按文档ID获取文档元数据与内容切片，供精确引用具体文档。"
                + "返回字段由 fields 控制(metadata/content/all)，内容切片按顺序返回。";
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
        Long documentId = ((Number) arguments.get("documentId")).longValue();
        String fields = arguments.containsKey("fields") && arguments.get("fields") != null
                ? String.valueOf(arguments.get("fields")).toLowerCase()
                : "all";
        int maxChunks = arguments.containsKey("maxChunks") && arguments.get("maxChunks") != null
                ? ((Number) arguments.get("maxChunks")).intValue()
                : 5;
        if (maxChunks < 1) {
            maxChunks = 5;
        }

        // 1. 加载文档元数据
        KbDocument doc = kbDocumentService.getById(documentId);
        if (doc == null) {
            return ToolResult.failure("文档不存在: " + documentId);
        }

        // 2. 权限校验：复用 DocPermissionService 走与 RAG 相同的 VIEW 权限链路
        Set<Long> allowed = docPermissionService.filterDocIds(
                ctx.getUserId(), doc.getKbId(), Set.of(documentId));
        if (!allowed.contains(documentId)) {
            log.warn("[DocumentRead] 用户={} 无权访问文档={} kb={}", ctx.getUserId(), documentId, doc.getKbId());
            return ToolResult.failure("无权访问该文档");
        }

        // 3. 按 fields 组装返回数据
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("documentId", doc.getId());
        boolean wantMeta = "all".equals(fields) || "metadata".equals(fields);
        boolean wantContent = "all".equals(fields) || "content".equals(fields);

        if (wantMeta) {
            data.put("metadata", buildMetadata(doc));
        }
        if (wantContent) {
            data.put("content", buildContent(documentId, maxChunks));
        }

        log.info("[DocumentRead] task={} doc={} kb={} fields={} chunks={}",
                ctx.getTaskId(), documentId, doc.getKbId(), fields,
                wantContent ? ((List<?>) data.get("content")).size() : 0);
        return ToolResult.success(data);
    }

    /** 文档元数据：选取对 Agent 引用有价值的字段（剔除存储路径/MD5 等内部字段） */
    private Map<String, Object> buildMetadata(KbDocument doc) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kbId", doc.getKbId());
        meta.put("originalName", doc.getOriginalName());
        meta.put("fileType", doc.getFileType());
        meta.put("fileSize", doc.getFileSize());
        meta.put("status", doc.getStatus());
        meta.put("chunkCount", doc.getChunkCount());
        meta.put("version", doc.getVersion());
        meta.put("reviewStatus", doc.getReviewStatus());
        meta.put("visibility", doc.getVisibility());
        meta.put("qualityScore", doc.getQualityScore());
        meta.put("effectiveFrom", doc.getEffectiveFrom());
        meta.put("expireAt", doc.getExpireAt());
        meta.put("createTime", doc.getCreateTime());
        meta.put("updateTime", doc.getUpdateTime());
        return meta;
    }

    /** 文档内容切片：按 chunkIndex 升序，截断到 maxChunks */
    private List<Map<String, Object>> buildContent(Long documentId, int maxChunks) {
        List<RetrievalResult> chunks = milvusService.queryByDocument(documentId, maxChunks);
        return chunks.stream()
                .map(this::toChunkView)
                .toList();
    }

    /** RetrievalResult → 精简切片视图（仅保留 Agent 引用所需字段，剔除 score/scoreType） */
    private Map<String, Object> toChunkView(RetrievalResult r) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("chunkIndex", r.getChunkIndex());
        view.put("chunkId", r.getChunkId());
        view.put("content", r.getText());
        view.put("source", r.getSource());
        return view;
    }
}
