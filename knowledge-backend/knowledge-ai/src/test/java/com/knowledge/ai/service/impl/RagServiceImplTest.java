/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.service.impl;

import com.knowledge.ai.chat.service.ChatContextService;
import com.knowledge.ai.chat.service.ChatMessageService;
import com.knowledge.ai.chat.service.ChatSessionService;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.ops.trace.TraceService;
import com.knowledge.ai.rag.cache.service.SemanticCacheService;
import com.knowledge.ai.rag.optimize.RagOptimizePipeline;
import com.knowledge.ai.search.SearchService;
import com.knowledge.ai.service.EmbeddingService;
import com.knowledge.ai.service.LLMService;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.kb.governance.service.KnowledgeGovernanceService;
import com.knowledge.kb.permission.service.DocPermissionService;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import com.knowledge.kb.storage.StorageService;
import dev.langchain4j.data.document.DocumentSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagServiceImpl 引用标注强制兜底（enforceCitations）单测。
 * <p>风格：plain JUnit5 + Mockito mock()，SUT 用 new 构造传 mock；
 * 私有方法经 {@link ReflectionTestUtils#invokeMethod} 调用。
 * 覆盖：已含引用跳过 / 漏标二次修复 / 修复无引用或编号越界回退 / 无命中与无信息兜底跳过 / 异常回退。
 */
class RagServiceImplTest {

    private LLMService llmService;
    private RagServiceImpl service;

    @BeforeEach
    void setUp() {
        llmService = mock(LLMService.class);
        // 19 个构造依赖：仅 llmService 被 enforceCitations 使用，其余传 mock 占位
        service = new RagServiceImpl(
                mock(KbDocumentService.class),
                mock(EmbeddingService.class),
                mock(MilvusService.class),
                mock(com.knowledge.ai.search.ElasticsearchService.class),
                mock(SearchService.class),
                llmService,
                mock(DocumentSplitter.class),
                mock(StorageService.class),
                new AiProperties(),
                mock(com.knowledge.ai.prompt.service.PromptTemplateService.class),
                mock(ChatContextService.class),
                mock(ChatSessionService.class),
                mock(ChatMessageService.class),
                mock(KbPermissionService.class),
                mock(DocPermissionService.class),
                mock(ApplicationEventPublisher.class),
                mock(KnowledgeGovernanceService.class),
                mock(RagOptimizePipeline.class),
                mock(TraceService.class),
                mock(SemanticCacheService.class));
    }

    private List<RetrievalResult> results(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> new RetrievalResult("文本" + i, 0.8, "src.pdf",
                        1L, i, "doc1_chunk" + i, "vector"))
                .toList();
    }

    private String enforce(String answer, List<RetrievalResult> results) {
        return ReflectionTestUtils.invokeMethod(service, "enforceCitations", answer, results, "问题");
    }

    @Test
    void 初稿已含引用时不触发二次修复() {
        List<RetrievalResult> results = results(2);
        String out = enforce("HDMI 是一种数字化接口 [1]。", results);
        assertEquals("HDMI 是一种数字化接口 [1]。", out);
        verify(llmService, never()).chat(anyString(), anyString());
    }

    @Test
    void 初稿缺失引用时二次修复并被采纳() {
        List<RetrievalResult> results = results(2);
        when(llmService.chat(anyString(), anyString()))
                .thenReturn("HDMI 是一种数字化接口 [1][2]。");
        String out = enforce("HDMI 是一种数字化接口。", results);
        assertEquals("HDMI 是一种数字化接口 [1][2]。", out);
    }

    @Test
    void 修复结果仍无引用时回退初稿() {
        List<RetrievalResult> results = results(2);
        when(llmService.chat(anyString(), anyString()))
                .thenReturn("HDMI 是一种数字化接口。");
        String out = enforce("HDMI 是一种数字化接口。", results);
        assertEquals("HDMI 是一种数字化接口。", out);
    }

    @Test
    void 修复编号越界时回退初稿() {
        List<RetrievalResult> results = results(2);
        when(llmService.chat(anyString(), anyString()))
                .thenReturn("HDMI 是一种数字化接口 [9]。");
        String out = enforce("HDMI 是一种数字化接口。", results);
        assertEquals("HDMI 是一种数字化接口。", out);
    }

    @Test
    void 无命中资料时不触发修复() {
        String out = enforce("随便回答", List.of());
        assertEquals("随便回答", out);
        verify(llmService, never()).chat(anyString(), anyString());
    }

    @Test
    void 无信息兜底回答不触发修复() {
        String out = enforce("知识库中暂无相关信息", results(2));
        assertEquals("知识库中暂无相关信息", out);
        verify(llmService, never()).chat(anyString(), anyString());
    }

    @Test
    void 修复调用异常时回退初稿() {
        List<RetrievalResult> results = results(2);
        when(llmService.chat(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM 故障"));
        String out = enforce("HDMI 是一种数字化接口。", results);
        assertEquals("HDMI 是一种数字化接口。", out);
    }
}
