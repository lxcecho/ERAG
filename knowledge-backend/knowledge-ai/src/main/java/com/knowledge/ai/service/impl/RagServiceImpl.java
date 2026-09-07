package com.knowledge.ai.service.impl;

import com.knowledge.ai.chat.service.ChatContextService;
import com.knowledge.ai.chat.service.ChatMessageService;
import com.knowledge.ai.chat.service.ChatSessionService;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.ChatResult;
import com.knowledge.ai.dto.IngestResult;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.ops.trace.Span;
import com.knowledge.ai.ops.trace.SpanType;
import com.knowledge.ai.ops.trace.TraceService;
import com.knowledge.ai.parser.MetadataPrefixEnricher;
import com.knowledge.ai.parser.SmartDocumentParser;
import com.knowledge.ai.prompt.PromptTemplates;
import com.knowledge.ai.prompt.service.PromptTemplateService;
import com.knowledge.ai.rag.cache.dto.CacheHit;
import com.knowledge.ai.rag.cache.service.SemanticCacheService;
import com.knowledge.ai.rag.optimize.RagOptimizePipeline;
import com.knowledge.ai.rag.optimize.dto.AnswerEvaluation;
import com.knowledge.ai.rag.optimize.dto.QueryContext;
import com.knowledge.ai.search.ElasticsearchService;
import com.knowledge.ai.search.SearchConstants;
import com.knowledge.ai.search.SearchService;
import com.knowledge.ai.service.EmbeddingService;
import com.knowledge.ai.service.LLMService;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.ai.service.RagService;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.security.PromptSanitizer;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.event.DocumentParsedEvent;
import com.knowledge.kb.governance.service.KnowledgeGovernanceService;
import com.knowledge.kb.permission.service.DocPermissionService;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import com.knowledge.kb.storage.StorageService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 编排服务实现。
 * <p>ingest：解析文档 → 切片 → 向量化 → Milvus 存储
 * <p>ask   ：检索 → DB模板组装系统提示 → 多轮历史 → LLM 同步回答 → 持久化问答记录
 * <p>askStream：同 ask 但以 SSE 逐 token 推送回答
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    /** 无命中资料的标准回复（与模板规则2一致，判定时据此跳过引用强制） */
    private static final String NO_INFO_ANSWER = "知识库中暂无相关信息";

    private final KbDocumentService kbDocumentService;
    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final ElasticsearchService elasticsearchService;
    private final SearchService searchService;
    private final LLMService llmService;
    private final DocumentSplitter documentSplitter;
    private final StorageService storageService;
    private final AiProperties aiProperties;
    private final PromptTemplateService promptTemplateService;
    private final ChatContextService chatContextService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final KbPermissionService kbPermissionService;
    private final DocPermissionService docPermissionService;
    private final ApplicationEventPublisher eventPublisher;
    private final KnowledgeGovernanceService governanceService;
    private final RagOptimizePipeline optimizePipeline;
    private final TraceService traceService;
    private final SemanticCacheService semanticCacheService;

    /** 智能文档解析器（支持MinerU/Tika自动切换） */
    private final SmartDocumentParser smartDocumentParser;

    /** 元数据前缀注入器 */
    private final MetadataPrefixEnricher metadataPrefixEnricher;

    /* ==================== 文档入库流程 ==================== */

    @Override
    public IngestResult ingest(Long documentId) {
        // 1. 读取文档元信息
        KbDocument doc = kbDocumentService.getById(documentId);
        if (doc == null) {
            throw new BizException("文档不存在: " + documentId);
        }

        // 2. 解析文件为纯文本（Tika 统一处理 PDF / Word / Markdown）
        String text = parseDocument(doc);
        if (text == null || text.isBlank()) {
            throw new BizException("文档内容为空，无法解析");
        }

        // 3. 文本切片：每个切片携带 kbId / documentId / source 元数据，供检索过滤与来源回溯
        List<TextSegment> segments = splitDocument(doc, text);
        if (segments.isEmpty()) {
            throw new BizException("文档切片结果为空");
        }

        // 4. 批量向量化（查询与入库必须用同一模型，保证相似度可比）
        List<Embedding> embeddings = embeddingService.embedAll(segments);

        // 5. 存入 Milvus（主索引）
        milvusService.store(embeddings, segments);

        // 6. 双写 ES（best-effort：失败不回滚 Milvus，仅标记 es_indexed=2 供重建补偿）
        try {
            elasticsearchService.store(segments);
            doc.setEsIndexed(1);
        } catch (Exception e) {
            log.warn("[RAG入库] ES双写失败 doc={}: {}", documentId, e.getMessage());
            doc.setEsIndexed(2);
        }
        kbDocumentService.updateById(doc);

        log.info("[RAG入库] doc={} chunks={} vectors={} esIndexed={}",
                documentId, segments.size(), embeddings.size(), doc.getEsIndexed());

        // 发布文档解析完成事件：由 knowledge-kb 治理监听器异步计算 SimHash 指纹 / 检测重复 / 质量评分
        eventPublisher.publishEvent(new DocumentParsedEvent(
                doc.getTenantId(), doc.getKbId(), documentId, text, segments.size(), doc.getMd5()));

        return new IngestResult(documentId, segments.size(), embeddings.size());
    }

    /**
     * 解析文档文件为文本
     * <p>
     * 通过存储抽象读取，自动适配 local / minio
     * 根据配置自动选择解析器：
     * - MinerU：PDF文件优先使用，输出结构化Markdown
     * - Tika：其他文件类型或MinerU不可用时的回退方案
     */
    private String parseDocument(KbDocument doc) {
        try (InputStream is = storageService.open(doc.getFilePath())) {
            log.info("[RAG入库] 解析文档: {}, 解析器: {}", doc.getOriginalName(), smartDocumentParser.getParserType());

            Document document = smartDocumentParser.parse(is, doc.getOriginalName());

            // 记录解析器信息到文档（用于统计和调试）
            String parserUsed = document.metadata().getString("parser");
            log.info("[RAG入库] 解析完成: {}, 使用解析器: {}, 文本长度: {}",
                    doc.getOriginalName(), parserUsed, document.text().length());

            return document.text();
        } catch (IOException e) {
            throw new BizException("文档读取失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[RAG入库] 文档解析失败: {}", doc.getOriginalName(), e);
            throw new BizException("文档解析失败: " + e.getMessage());
        }
    }

    /**
     * 切片并注入元数据。
     * <p>
     * 流程：
     * 1. 使用DocumentSplitter切分文档
     * 2. 注入基础元数据（kbId, documentId, source, chunkIndex, chunkId）
     * 3. 注入前缀元数据（文档名 + 章节路径）
     * 4. 处理Parent-Child分块
     */
    private List<TextSegment> splitDocument(KbDocument doc, String text) {
        Metadata metadata = new Metadata();
        metadata.put("kbId", String.valueOf(doc.getKbId()));
        metadata.put("documentId", String.valueOf(doc.getId()));
        metadata.put("source", doc.getOriginalName());
        Document document = Document.from(text, metadata);
        List<TextSegment> segments = documentSplitter.split(document);

        var pcConfig = aiProperties.getRag().getParentChild();
        boolean parentChildEnabled = pcConfig.isEnabled() && pcConfig.getGroupSize() > 1;

        for (int i = 0; i < segments.size(); i++) {
            segments.get(i).metadata().put("chunkIndex", String.valueOf(i));
            // 确定性 chunkId：作为 ES _id 与 Milvus metadata，供混合检索两路去重对齐（幂等覆盖）
            segments.get(i).metadata().put("chunkId", "doc" + doc.getId() + "_chunk" + i);

            // Parent-Child：为小切片注入父段文本，检索命中后用父段喂 LLM（上下文更完整）
            if (parentChildEnabled) {
                int groupSize = pcConfig.getGroupSize();
                int groupStart = (i / groupSize) * groupSize;
                int groupEnd = Math.min(groupStart + groupSize, segments.size());
                StringBuilder parentText = new StringBuilder();
                for (int j = groupStart; j < groupEnd; j++) {
                    if (j > groupStart) parentText.append("\n");
                    parentText.append(segments.get(j).text());
                }
                segments.get(i).metadata().put(SearchConstants.FIELD_PARENT_TEXT, parentText.toString());
                segments.get(i).metadata().put(SearchConstants.FIELD_PARENT_ID,
                        "doc" + doc.getId() + "_parent" + (i / groupSize));
            }
        }

        // 元数据增强：注入文档名 + 章节路径前缀（提升向量检索命中率）
        segments = metadataPrefixEnricher.enrich(segments, doc.getOriginalName());

        log.info("[RAG入库] 切片完成: doc={}, chunks={}, parentChild={}", doc.getId(), segments.size(), parentChildEnabled);
        return segments;
    }

    /* ==================== 问答流程 ==================== */

    @Override
    public ChatResult ask(String question, Long kbId, Long sessionId) {
        Span root = traceService.startRoot("rag.ask");
        try {
            root.attribute("kbId", kbId).attribute("question.length", question.length());
            ChatResult result = doAsk(question, kbId, sessionId);
            root.success();
            return result;
        } catch (RuntimeException e) {
            root.error(e);
            throw e;
        } finally {
            root.close();
        }
    }

    /** ask 实际实现（由 {@link #ask} 包裹 ROOT span 链路追踪） */
    private ChatResult doAsk(String question, Long kbId, Long sessionId) {
        // 【安全】Prompt 注入防护：检测并记录可疑输入
        if (PromptSanitizer.containsSuspiciousPattern(question)) {
            log.warn("[安全] 检测到可疑 prompt 注入尝试 userId={} kbId={} questionPreview={}",
                    SecurityUtils.currentUserId(), kbId,
                    question.length() > 100 ? question.substring(0, 100) + "..." : question);
        }
        // 清洗用户输入：移除注入模式 + 截断超长内容
        question = PromptSanitizer.sanitize(question);

        Long userId = SecurityUtils.currentUserId();
        // 权限隔离：需 viewer 及以上权限才能检索该知识库
        kbPermissionService.checkViewer(kbId, userId);
        // 确保会话存在（sessionId 为空则新建）
        Long sid = chatSessionService.ensureSession(sessionId, kbId, userId, question);
        // 加载历史（在保存当前问题之前，确保历史不含本轮问题）
        List<ChatMessage> history = chatContextService.loadHistory(sid);

        // 【语义缓存 · 第一层】MD5 精确匹配（无需 embedding，O(1) 数据库查询）
        if (aiProperties.getRag().getSemanticCache().isEnabled()) {
            Long tid = TenantContext.getTenantId();
            java.util.Optional<CacheHit> exactHit = semanticCacheService.exactLookup(tid, kbId, question);
            if (exactHit.isPresent()) {
                CacheHit hit = exactHit.get();
                chatMessageService.saveUserMessage(sid, question);
                chatMessageService.saveAssistantMessage(sid, hit.answer(), hit.sources());
                log.info("[RAG问答] MD5 精确缓存命中 sid={}", sid);
                return new ChatResult(hit.answer(), hit.sources(), sid);
            }
        }

        // 【语义缓存 · 第二层】向量余弦相似度匹配
        Embedding cacheEmbedding = null;
        if (aiProperties.getRag().getSemanticCache().isEnabled()) {
            cacheEmbedding = embeddingService.embed(question);
            Long tid = TenantContext.getTenantId();
            java.util.Optional<CacheHit> cacheHit = semanticCacheService.lookup(tid, kbId, cacheEmbedding);
            if (cacheHit.isPresent()) {
                CacheHit hit = cacheHit.get();
                chatMessageService.saveUserMessage(sid, question);
                chatMessageService.saveAssistantMessage(sid, hit.answer(), hit.sources());
                log.info("[RAG问答] 语义缓存命中 sid={} similarity={}", sid, hit.similarity());
                return new ChatResult(hit.answer(), hit.sources(), sid);
            }
        }

        // 【RAG Trace · 阶段1】查询准备：改写+扩展
        QueryContext queryContext;
        try (Span span = traceService.startSpan("rag.query.prepare", SpanType.PIPELINE)) {
            span.attribute("question", question.length() > 200 ? question.substring(0, 200) : question);
            queryContext = optimizePipeline.prepareQuery(question, history);
            span.attribute("primaryQuery", queryContext.primaryQuery());
            span.attribute("subQueries.count", queryContext.subQueries().size());
            span.success();
        }

        // 【RAG Trace · 阶段2】向量化
        Embedding queryEmbedding;
        try (Span span = traceService.startSpan("rag.embedding", SpanType.LLM)) {
            queryEmbedding = embeddingService.embed(queryContext.primaryQuery());
            span.attribute("vectorDim", queryEmbedding.vector().length);
            span.success();
        }

        // 【RAG Trace · 阶段3】混合检索（BM25 + 向量 + RRF 融合 + Rerank）
        int ragTopK = aiProperties.getRag().getTopK();
        int initTopK = Math.max(ragTopK * 4, 50);
        List<RetrievalResult> results;
        try (Span span = traceService.startSpan("rag.hybrid.search", SpanType.SEARCH)) {
            span.attribute("kbId", kbId).attribute("initTopK", initTopK).attribute("ragTopK", ragTopK);
            results = optimizePipeline.retrieve(queryContext, queryEmbedding, kbId, initTopK);
            span.attribute("hits.count", results.size());
            if (!results.isEmpty()) {
                span.attribute("hits.topScore", results.get(0).getScore());
            }
            span.success();
        }

        // 【文档级权限 · 必走 Post-Filter】先批量取得用户可见 docId 集合，再基于 docId 移除无权 chunk
        java.util.Set<Long> allowDocIds = docPermissionService.filterDocIds(userId, kbId,
                results.stream().map(RetrievalResult::getDocumentId).filter(java.util.Objects::nonNull).toList());
        results = results.stream()
                .filter(r -> r.getDocumentId() != null && allowDocIds.contains(r.getDocumentId()))
                .toList();
        // 治理门禁：排除未审核 / 已过期文档
        results = applyGovernanceFilter(results);

        // 【RAG Trace · 阶段4】上下文压缩
        try (Span span = traceService.startSpan("rag.compress", SpanType.PIPELINE)) {
            int beforeSize = results.size();
            results = optimizePipeline.compress(results, question);
            if (results.size() > ragTopK) results = results.subList(0, ragTopK);
            span.attribute("before", beforeSize).attribute("after", results.size());
            span.success();
        }

        // 持久化用户问题
        chatMessageService.saveUserMessage(sid, question);

        // 无命中：直接返回无资料提示，避免 LLM 凭空作答
        if (results.isEmpty()) {
            log.info("[RAG问答] kb={} 无命中资料", kbId);
            String answer = "知识库中暂无相关信息";
            chatMessageService.saveAssistantMessage(sid, answer, results);
            if (cacheEmbedding != null) {
                semanticCacheService.save(TenantContext.getTenantId(), kbId,
                        question, cacheEmbedding, answer, results);
            }
            return new ChatResult(answer, results, sid);
        }

        // 组装上下文 + 系统提示
        String systemPrompt = buildSystemPrompt(results);

        // 【RAG Trace · 阶段5】LLM 生成回答
        String answer;
        try (Span span = traceService.startSpan("rag.llm.generate", SpanType.LLM)) {
            span.attribute("contextChunks", results.size());
            span.attribute("historyTurns", history.size());
            answer = llmService.chat(systemPrompt, history, question);
            span.attribute("answerLen", answer.length());
            span.success();
        }

        // 【引用强制】漏标时二次修复
        answer = enforceCitations(answer, results, question);
        chatMessageService.saveAssistantMessage(sid, answer, results);
        // 语义缓存写入
        if (cacheEmbedding != null) {
            semanticCacheService.save(TenantContext.getTenantId(), kbId,
                    question, cacheEmbedding, answer, results);
        }
        // 回答评价（best-effort）
        AnswerEvaluation evaluation = optimizePipeline.evaluate(question, results, answer);
        log.info("[RAG问答] kb={} 命中 {} 条资料 sid={} eval={}", kbId, results.size(), sid,
                evaluation == null ? "off" : evaluation.overallScore());
        return new ChatResult(answer, results, sid, evaluation);
    }

    @Override
    public void askStream(String question, Long kbId, Long sessionId, SseEmitter emitter) {
        // 【安全】Prompt 注入防护
        if (PromptSanitizer.containsSuspiciousPattern(question)) {
            log.warn("[安全] 流式问答检测到可疑 prompt 注入尝试 kbId={} questionPreview={}",
                    kbId, question.length() > 100 ? question.substring(0, 100) + "..." : question);
        }
        question = PromptSanitizer.sanitize(question);
        // sanitize 后 question 不再变化，拷贝为 final 供内部类引用
        final String sanitizedQuestion = question;

        Span root = traceService.startRoot("rag.ask.stream");
        try {
            root.attribute("kbId", kbId).attribute("question.length", question.length());
            Long userId = SecurityUtils.currentUserId();
            kbPermissionService.checkViewer(kbId, userId);
            Long sid = chatSessionService.ensureSession(sessionId, kbId, userId, question);
            List<ChatMessage> history = chatContextService.loadHistory(sid);

            // 【语义缓存 · 第一层】MD5 精确匹配
            if (aiProperties.getRag().getSemanticCache().isEnabled()) {
                Long tid = TenantContext.getTenantId();
                java.util.Optional<CacheHit> exactHit = semanticCacheService.exactLookup(tid, kbId, question);
                if (exactHit.isPresent()) {
                    CacheHit hit = exactHit.get();
                    emitter.send(SseEmitter.event().name("sources").data(hit.sources(), MediaType.APPLICATION_JSON));
                    chatMessageService.saveUserMessage(sid, question);
                    chatMessageService.saveAssistantMessage(sid, hit.answer(), hit.sources());
                    emitter.send(SseEmitter.event().name("token").data(hit.answer()));
                    emitter.send(SseEmitter.event().name("done").data(String.valueOf(sid)));
                    emitter.complete();
                    log.info("[RAG流式] MD5 精确缓存命中 sid={}", sid);
                    root.success();
                    return;
                }
            }

            // 【语义缓存 · 第二层】向量余弦相似度匹配
            Embedding cacheEmbeddingTemp = null;
            if (aiProperties.getRag().getSemanticCache().isEnabled()) {
                cacheEmbeddingTemp = embeddingService.embed(question);
                Long tid = TenantContext.getTenantId();
                java.util.Optional<CacheHit> cacheHit = semanticCacheService.lookup(tid, kbId, cacheEmbeddingTemp);
                if (cacheHit.isPresent()) {
                    CacheHit hit = cacheHit.get();
                    emitter.send(SseEmitter.event().name("sources").data(hit.sources(), MediaType.APPLICATION_JSON));
                    chatMessageService.saveUserMessage(sid, question);
                    chatMessageService.saveAssistantMessage(sid, hit.answer(), hit.sources());
                    emitter.send(SseEmitter.event().name("token").data(hit.answer()));
                    emitter.send(SseEmitter.event().name("done").data(String.valueOf(sid)));
                    emitter.complete();
                    log.info("[RAG流式] 语义缓存命中 sid={} similarity={}", sid, hit.similarity());
                    root.success();
                    return;
                }
            }
            final Embedding cacheEmbedding = cacheEmbeddingTemp;
            final Long cacheTid = TenantContext.getTenantId();

            // 【RAG 优化流水线 · 阶段1+2】查询准备 + 三查询解耦检索；初召放大 4x，给 Post-Filter 留余量
            // 先推送状态事件，前端在首个 token 前展示"正在检索知识库…"思考占位
            emitter.send(SseEmitter.event().name("status").data("retrieving"));
            QueryContext queryContext = optimizePipeline.prepareQuery(question, history);
            Embedding queryEmbedding = embeddingService.embed(queryContext.primaryQuery());
            int ragTopK = aiProperties.getRag().getTopK();
            int initTopK = Math.max(ragTopK * 4, 50);
            List<RetrievalResult> results = optimizePipeline.retrieve(queryContext, queryEmbedding, kbId, initTopK);

            // 【文档级权限 · 必走 Post-Filter】整批取得用户可见 docId，再基于 docId 移除无权 chunk
            java.util.Set<Long> allowDocIds = docPermissionService.filterDocIds(userId, kbId,
                    results.stream().map(RetrievalResult::getDocumentId).filter(java.util.Objects::nonNull).toList());
            results = results.stream()
                    .filter(r -> r.getDocumentId() != null && allowDocIds.contains(r.getDocumentId()))
                    .toList();
            // 治理门禁：排除未审核 / 已过期文档（依据 kb.governance 配置，开关关闭时原样返回）
            results = applyGovernanceFilter(results);
            // 【RAG 优化流水线 · 阶段3】上下文压缩：去重 + 预算截断（best-effort）
            results = optimizePipeline.compress(results, question);
            if (results.size() > ragTopK) results = results.subList(0, ragTopK);

            // 先推送引用来源事件，前端可立即渲染来源卡片
            emitter.send(SseEmitter.event().name("sources").data(results, MediaType.APPLICATION_JSON));
            // 持久化用户问题
            chatMessageService.saveUserMessage(sid, question);

            // 无命中：推送提示并结束
            if (results.isEmpty()) {
                log.info("[RAG流式] kb={} 无命中资料", kbId);
                String answer = "知识库中暂无相关信息";
                chatMessageService.saveAssistantMessage(sid, answer, results);
                // 语义缓存写入（含"无资料"回答）
                if (cacheEmbedding != null) {
                    semanticCacheService.save(cacheTid, kbId, question, cacheEmbedding, answer, results);
                }
                emitter.send(SseEmitter.event().name("token").data(answer));
                emitter.send(SseEmitter.event().name("done").data(String.valueOf(sid)));
                emitter.complete();
                return;
            }

            String systemPrompt = buildSystemPrompt(results);
            // 检索完成、开始生成：推送状态事件，前端切换为"正在生成回答…"
            emitter.send(SseEmitter.event().name("status").data("generating"));
            // 累积完整回答，流式结束后落库
            StringBuilder full = new StringBuilder();
            final Long finalSid = sid;
            final List<RetrievalResult> finalResults = results;

            llmService.streamChat(systemPrompt, history, question, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partial) {
                    full.append(partial);
                    try {
                        emitter.send(SseEmitter.event().name("token").data(partial));
                    } catch (IOException e) {
                        log.warn("[RAG流式] token 推送失败: {}", e.getMessage());
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    // 【引用强制】流式结束后校验，漏标则二次修复；修复成功时推送 correct 事件由前端整体替换
                    String answer = enforceCitations(full.toString(), finalResults, sanitizedQuestion);
                    chatMessageService.saveAssistantMessage(finalSid, answer, finalResults);
                    // 语义缓存写入：将本次问答缓存，后续相似问题直接命中
                    if (cacheEmbedding != null) {
                        semanticCacheService.save(cacheTid, kbId, sanitizedQuestion, cacheEmbedding, answer, finalResults);
                    }
                    try {
                        // 【RAG 优化流水线 · 阶段5】回答评价：流式仅跑规则版（allowLlm=false），
                        //   避免 LLM 评价往返阻塞 done 事件；evaluation 事件先于 done 推送
                        AnswerEvaluation evaluation = optimizePipeline.evaluate(
                                sanitizedQuestion, finalResults, answer, false);
                        if (evaluation != null) {
                            emitter.send(SseEmitter.event().name("evaluation")
                                    .data(evaluation, MediaType.APPLICATION_JSON));
                        }
                        if (!answer.equals(full.toString())) {
                            emitter.send(SseEmitter.event().name("correct").data(answer));
                        }
                        emitter.send(SseEmitter.event().name("done").data(String.valueOf(finalSid)));
                        emitter.complete();
                    } catch (IOException e) {
                        log.warn("[RAG流式] done 推送失败: {}", e.getMessage());
                    }
                }

                @Override
                public void onError(Throwable error) {
                    log.error("[RAG流式] 生成失败 sid={}", finalSid, error);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                    } catch (IOException ignored) {
                        // 发射器已关闭，忽略
                    }
                    emitter.completeWithError(error);
                }
            });
            log.info("[RAG流式] kb={} 命中 {} 条资料 sid={}", kbId, results.size(), sid);
        } catch (Exception e) {
            root.error(e);
            log.error("[RAG流式] 异常", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        } finally {
            root.close();
        }
    }

    /** 组装上下文并解析系统提示词（DB 模板优先） */
    private String buildSystemPrompt(List<RetrievalResult> results) {
        return promptTemplateService.resolveRagSystemPrompt(buildContext(results));
    }

    /** 检索结果 → 编号参考资料上下文文本（系统提示词与引用修复复用） */
    private String buildContext(List<RetrievalResult> results) {
        List<String> texts = results.stream().map(RetrievalResult::getText).toList();
        List<String> sources = results.stream().map(RetrievalResult::getSource)
                .map(s -> s == null ? "未知" : s).collect(Collectors.toList());
        return PromptTemplates.buildContext(texts, sources);
    }

    /**
     * 引用标注强制兜底：RAG 回答必须带 [编号] 引用。
     * <p>模型对提示词"必须标注引用"规则的遵循存在概率性，个别问题/轮次会漏标。
     * 当命中资料非空、回答非"无信息"兜底、且不含任何 [编号] 时，用二次 LLM 调用
     * （{@link PromptTemplates#CITATION_REPAIR_TEMPLATE}）修复：
     * 仅补引用标注、不改实质内容；修复后仍无引用或编号越界（幻觉）则回退初稿。
     * 正常带引用的回答零额外开销。
     *
     * @param answer   初稿回答
     * @param results  检索结果（非空才需要引用）
     * @param question 用户问题
     * @return 含合法引用的回答（修复失败时回退初稿）
     */
    private String enforceCitations(String answer, List<RetrievalResult> results, String question) {
        if (results.isEmpty() || answer == null || answer.isBlank()
                || answer.contains(NO_INFO_ANSWER) || PromptTemplates.containsCitation(answer)) {
            return answer;
        }
        String repairPrompt = PromptTemplates.buildRepairPrompt(buildContext(results), answer);
        try {
            String repaired = llmService.chat(repairPrompt, question);
            if (PromptTemplates.containsCitation(repaired)
                    && PromptTemplates.citationsWithinRange(repaired, results.size())) {
                log.info("[RAG引用] 初稿缺失引用，已二次修复 answerLen={}->{}", answer.length(), repaired.length());
                return repaired;
            }
            log.warn("[RAG引用] 修复结果仍无引用或编号越界，回退初稿 answerLen={} repairedLen={}",
                    answer.length(), repaired == null ? 0 : repaired.length());
        } catch (RuntimeException e) {
            log.warn("[RAG引用] 修复调用失败，回退初稿: {}", e.getMessage());
        }
        return answer;
    }

    /**
     * 治理门禁过滤：依据 kb.governance.review-gate / expire-gate 开关，
     * 剔除未审核或已过期的文档命中。开关均关闭时原样返回（零影响降级）。
     */
    private List<RetrievalResult> applyGovernanceFilter(List<RetrievalResult> results) {
        java.util.Set<Long> governanceValid = governanceService.filterGovernanceValid(
                results.stream().map(RetrievalResult::getDocumentId).filter(java.util.Objects::nonNull).toList());
        return results.stream()
                .filter(r -> r.getDocumentId() == null || governanceValid.contains(r.getDocumentId()))
                .toList();
    }

    /* ==================== 普通对话流程（不检索知识库） ==================== */

    @Override
    public ChatResult plainChat(String question, Long kbId, Long sessionId) {
        Span root = traceService.startRoot("chat.plain");
        try {
            root.attribute("kbId", kbId).attribute("question.length", question.length());
            Long userId = SecurityUtils.currentUserId();
            // 普通对话不做 KB 权限校验；kbId 可空，ensureSession 内兜底为 0
            Long sid = chatSessionService.ensureSession(sessionId, kbId, userId, question);
            List<ChatMessage> history = chatContextService.loadHistory(sid);
            // 持久化用户问题（在加载历史之后，确保历史不含本轮）
            chatMessageService.saveUserMessage(sid, question);

            String systemPrompt = promptTemplateService.resolvePlainSystemPrompt();
            String answer = llmService.chat(systemPrompt, history, question);
            List<RetrievalResult> emptySources = List.of();
            chatMessageService.saveAssistantMessage(sid, answer, emptySources);
            root.success();
            log.info("[普通对话] sid={} answerLen={}", sid, answer.length());
            return new ChatResult(answer, emptySources, sid);
        } catch (RuntimeException e) {
            root.error(e);
            throw e;
        } finally {
            root.close();
        }
    }

    @Override
    public void plainChatStream(String question, Long kbId, Long sessionId, SseEmitter emitter) {
        Span root = traceService.startRoot("chat.plain.stream");
        try {
            root.attribute("kbId", kbId).attribute("question.length", question.length());
            Long userId = SecurityUtils.currentUserId();
            Long sid = chatSessionService.ensureSession(sessionId, kbId, userId, question);
            List<ChatMessage> history = chatContextService.loadHistory(sid);
            // 持久化用户问题
            chatMessageService.saveUserMessage(sid, question);

            String systemPrompt = promptTemplateService.resolvePlainSystemPrompt();
            // 累积完整回答，流式结束后落库
            StringBuilder full = new StringBuilder();
            final Long finalSid = sid;

            llmService.streamChat(systemPrompt, history, question, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partial) {
                    full.append(partial);
                    try {
                        emitter.send(SseEmitter.event().name("token").data(partial));
                    } catch (IOException e) {
                        log.warn("[普通流式] token 推送失败: {}", e.getMessage());
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    chatMessageService.saveAssistantMessage(finalSid, full.toString(), List.of());
                    try {
                        emitter.send(SseEmitter.event().name("done").data(String.valueOf(finalSid)));
                        emitter.complete();
                    } catch (IOException e) {
                        log.warn("[普通流式] done 推送失败: {}", e.getMessage());
                    }
                }

                @Override
                public void onError(Throwable error) {
                    log.error("[普通流式] 生成失败 sid={}", finalSid, error);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                    } catch (IOException ignored) {
                        // 发射器已关闭，忽略
                    }
                    emitter.completeWithError(error);
                }
            });
            log.info("[普通流式] sid={} 已启动", sid);
        } catch (Exception e) {
            root.error(e);
            log.error("[普通流式] 异常", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        } finally {
            root.close();
        }
    }
}
