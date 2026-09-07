package com.knowledge.agent.tool;

import com.knowledge.agent.dto.Evidence;
import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.engine.LlmResult;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.search.SearchService;
import com.knowledge.ai.service.EmbeddingService;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.permission.service.DocPermissionService;
import com.knowledge.kb.service.KbDocumentService;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档比较工具：对比两篇文档的内容差异，输出结构化对比报告。
 * <p>
 * 流程：
 * <ol>
 *   <li>获取两篇文档元数据（名称/类型/切片数/状态）；</li>
 *   <li>文档级权限校验（{@link DocPermissionService#filterDocIds}），无 VIEW 权限的文档拒绝比较；</li>
 *   <li>尽力检索各文档的内容切片（以文档名为查询做混合检索，按 documentId 过滤）；</li>
 *   <li>调用 LLM 生成对比报告（相同点 / 差异点 / 建议）。</li>
 * </ol>
 * <p>
 * 权限安全：与 RAG 检索走相同的 {@code DocPermissionService} 链路，无法越权比较不可见文档。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCompareTool implements Tool {

    private final KbDocumentService kbDocumentService;
    private final DocPermissionService docPermissionService;
    private final SearchService searchService;
    private final EmbeddingService embeddingService;
    private final AgentLlmCaller llmCaller;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "documentId1": {"type": "integer", "description": "第一篇文档ID"},
                "documentId2": {"type": "integer", "description": "第二篇文档ID"},
                "focus": {"type": "string", "description": "对比关注点（如'政策条款差异'），可空"}
              },
              "required": ["documentId1", "documentId2"]
            }""";

    private static final String SYSTEM_PROMPT = """
            你是企业知识库文档对比分析专家。基于两篇文档的元数据与内容切片，输出结构化对比报告。
            要求：
            - 分"相同点""差异点""建议"三部分
            - 差异点需具体到条款/数据/结论层面
            - 资料不足的维度标注"[资料不足]"
            - 使用 Markdown 格式""";

    @Override
    public String name() {
        return "document_compare";
    }

    @Override
    public String description() {
        return "对比两篇文档的内容差异，输出结构化对比报告（相同点/差异点/建议）。"
                + "适用于政策版本对比、方案差异分析等场景。";
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
        Long docId1 = ((Number) arguments.get("documentId1")).longValue();
        Long docId2 = ((Number) arguments.get("documentId2")).longValue();
        String focus = (String) arguments.get("focus");

        // 1. 获取文档元数据
        KbDocument doc1 = kbDocumentService.getById(docId1);
        KbDocument doc2 = kbDocumentService.getById(docId2);
        if (doc1 == null || doc2 == null) {
            return ToolResult.failure("文档不存在（docId1=" + docId1 + ", docId2=" + docId2 + "）");
        }

        // 2. 文档级权限校验
        Set<Long> allowed = docPermissionService.filterDocIds(
                ctx.getUserId(), ctx.getKbId(), List.of(docId1, docId2));
        if (!allowed.contains(docId1) || !allowed.contains(docId2)) {
            log.warn("[DocCompare] 用户={} 无权限比较文档 doc1={} doc2={}",
                    ctx.getUserId(), docId1, docId2);
            return ToolResult.failure("无权限访问指定文档（文档可能为私有且未授权）");
        }

        // 3. 尽力检索各文档内容切片
        String content1 = retrieveDocContent(doc1, ctx.getKbId());
        String content2 = retrieveDocContent(doc2, ctx.getKbId());

        // 4. LLM 生成对比报告
        String userPrompt = buildPrompt(doc1, doc2, content1, content2, focus);
        log.info("[DocCompare] task={} 比较文档 {} vs {}", ctx.getTaskId(), docId1, docId2);

        LlmResult llm = llmCaller.call(SYSTEM_PROMPT, userPrompt, "document_compare",
                LlmIdentity.of(ctx.getUserId(), ctx.getTenantId()));
        String report = llm.text();
        int tokens = llm.totalTokens();

        if (report == null || report.isBlank()) {
            return ToolResult.failure("对比报告生成结果为空");
        }
        return ToolResult.success(report, tokens);
    }

    /** 尽力检索文档内容：以文档名为查询做混合检索，按 documentId 过滤取 top 5 切片 */
    private String retrieveDocContent(KbDocument doc, Long kbId) {
        try {
            Embedding emb = embeddingService.embed(doc.getOriginalName());
            List<RetrievalResult> hits = searchService.hybridSearch(doc.getOriginalName(), emb, kbId, 10);
            String content = hits.stream()
                    .filter(r -> doc.getId().equals(r.getDocumentId()))
                    .limit(5)
                    .map(RetrievalResult::getText)
                    .reduce((a, b) -> a + "\n---\n" + b)
                    .orElse("");
            return content.isBlank() ? "（未检索到内容切片）" : content;
        } catch (Exception e) {
            log.warn("[DocCompare] 文档={} 内容检索失败: {}", doc.getId(), e.getMessage());
            return "（内容检索失败）";
        }
    }

    private static String buildPrompt(KbDocument doc1, KbDocument doc2,
                                       String content1, String content2, String focus) {
        return "对比文档A与文档B。\n\n"
                + "## 文档A\n- 文件名: " + doc1.getOriginalName()
                + "\n- 类型: " + doc1.getFileType()
                + "\n- 切片数: " + doc1.getChunkCount()
                + "\n- 内容片段:\n" + content1 + "\n\n"
                + "## 文档B\n- 文件名: " + doc2.getOriginalName()
                + "\n- 类型: " + doc2.getFileType()
                + "\n- 切片数: " + doc2.getChunkCount()
                + "\n- 内容片段:\n" + content2 + "\n\n"
                + (focus != null ? "对比关注点: " + focus + "\n\n" : "")
                + "请输出对比报告。";
    }
}
