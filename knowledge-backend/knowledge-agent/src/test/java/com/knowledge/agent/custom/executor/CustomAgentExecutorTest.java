/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.custom.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.custom.config.CustomAgentProperties;
import com.knowledge.agent.custom.dto.ChatStartRequest;
import com.knowledge.agent.custom.entity.AgentDefinition;
import com.knowledge.agent.custom.entity.AgentRun;
import com.knowledge.agent.custom.mapper.AgentRunMapper;
import com.knowledge.agent.custom.service.LogUploadService;
import com.knowledge.agent.memory.MemoryService;
import com.knowledge.ai.service.EmbeddingService;
import com.knowledge.ai.service.LLMService;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.permission.service.DocPermissionService;
import com.knowledge.kb.service.KbPermissionService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CustomAgentExecutor} 单元测试：single 流式（mock LLM 回调）、multi 多步产物传递、步骤失败终止。
 */
@ExtendWith(MockitoExtension.class)
class CustomAgentExecutorTest {

    @Mock
    private LLMService llmService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private MilvusService milvusService;

    @Mock
    private DocPermissionService docPermissionService;

    @Mock
    private KbPermissionService kbPermissionService;

    @Mock
    private LogUploadService logUploadService;

    @Mock
    private AgentRunMapper runMapper;

    @Mock
    private MemoryService memoryService;

    @Spy
    private CustomAgentProperties props = new CustomAgentProperties();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CustomAgentExecutor executor;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AgentDefinition contentDef() {
        AgentDefinition def = new AgentDefinition();
        def.setId(1L);
        def.setName("分析助手");
        def.setSystemPrompt("请基于 {context} 分析 {question}");
        def.setSourceMode("content");
        def.setExecMode("single");
        return def;
    }

    private ChatStartRequest contentReq(String question) {
        ChatStartRequest req = new ChatStartRequest();
        req.setQuestion(question);
        req.setInputType("content");
        req.setContent("参考日志内容");
        return req;
    }

    /* ---------- single 流式 ---------- */

    @Test
    void should_stream_tokens_and_complete_run() throws Exception {
        AgentDefinition def = contentDef();
        AgentRun run = new AgentRun();
        run.setId(10L);
        run.setQuestion("根因是什么？");
        SseEmitter emitter = new SseEmitter();

        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(3);
            handler.onPartialResponse("根据日志");
            handler.onPartialResponse("，根因是超时");
            // 执行器不读取 ChatResponse（结果由累加 token 得到），mock 即可
            handler.onCompleteResponse(mock(ChatResponse.class));
            return null;
        }).when(llmService).streamChat(anyString(), any(), anyString(), any(StreamingChatResponseHandler.class));

        CustomAgentExecutor.ContextInfo ctx = executor.prepareContext(def, contentReq("根因是什么？"), 1L);

        executor.executeSingle(run, def, ctx, emitter, null);

        assertEquals("COMPLETED", run.getStatus());
        assertEquals("根据日志，根因是超时", run.getResult());
        assertTrue(run.getFinishedTime() != null);
        // executeSingle 先置 RUNNING 再置 COMPLETED，共更新 2 次
        verify(runMapper, times(2)).updateById(run);
    }

    @Test
    void should_mark_run_failed_on_llm_error() {
        AgentDefinition def = contentDef();
        AgentRun run = new AgentRun();
        run.setId(11L);
        run.setQuestion("q");
        SseEmitter emitter = new SseEmitter();

        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(3);
            handler.onError(new RuntimeException("模型接口超时"));
            return null;
        }).when(llmService).streamChat(anyString(), any(), anyString(), any(StreamingChatResponseHandler.class));

        CustomAgentExecutor.ContextInfo ctx = executor.prepareContext(def, contentReq("q"), 1L);
        executor.executeSingle(run, def, ctx, emitter, null);

        assertEquals("FAILED", run.getStatus());
        assertTrue(run.getErrorMsg().contains("模型接口超时"));
    }

    @Test
    void should_inject_memory_text_into_system_prompt_when_provided() throws Exception {
        AgentDefinition def = contentDef();
        AgentRun run = new AgentRun();
        run.setId(14L);
        run.setQuestion("我叫什么？");
        SseEmitter emitter = new SseEmitter();

        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(3);
            handler.onPartialResponse("你叫小明");
            handler.onCompleteResponse(mock(ChatResponse.class));
            return null;
        }).when(llmService).streamChat(anyString(), any(), anyString(), any(StreamingChatResponseHandler.class));

        CustomAgentExecutor.ContextInfo ctx = executor.prepareContext(def, contentReq("我叫什么？"), 1L);
        executor.executeSingle(run, def, ctx, emitter, "【近期对话】\nuser: 我叫小明\nassistant: 你好小明\n");

        // 记忆文本须拼入 systemPrompt 传给 LLM
        ArgumentCaptor<String> sysCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).streamChat(sysCaptor.capture(), any(), anyString(), any(StreamingChatResponseHandler.class));
        assertTrue(sysCaptor.getValue().contains("用户历史记忆"));
        assertTrue(sysCaptor.getValue().contains("我叫小明"));
    }

    @Test
    void should_record_interaction_when_session_id_present() throws Exception {
        AgentDefinition def = contentDef();
        AgentRun run = new AgentRun();
        run.setId(15L);
        run.setTenantId(1L);
        run.setUserId(1L);
        run.setSessionId(100L);
        run.setQuestion("我叫什么？");
        SseEmitter emitter = new SseEmitter();

        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(3);
            handler.onPartialResponse("你叫小明");
            handler.onCompleteResponse(mock(ChatResponse.class));
            return null;
        }).when(llmService).streamChat(anyString(), any(), anyString(), any(StreamingChatResponseHandler.class));

        CustomAgentExecutor.ContextInfo ctx = executor.prepareContext(def, contentReq("我叫什么？"), 1L);
        executor.executeSingle(run, def, ctx, emitter, null);

        // 完整回答在 onCompleteResponse 时写入记忆中心
        verify(memoryService).recordInteraction(eq(100L), eq(1L), eq(1L), eq("我叫什么？"), eq("你叫小明"));
    }

    @Test
    void should_prepare_plain_context_without_retrieval() {
        AgentDefinition def = contentDef();
        def.setSourceMode("kb");
        ChatStartRequest req = new ChatStartRequest();
        req.setQuestion("q");
        req.setInputType("plain");

        CustomAgentExecutor.ContextInfo ctx = executor.prepareContext(def, req, 1L);

        assertEquals("", ctx.getContext());
        assertEquals("plain", ctx.getInputType());
        assertTrue(ctx.getKbId() == null);
        // plain 模式不触发知识库检索与权限校验（对话框关闭知识库检索开关）
        verifyNoInteractions(kbPermissionService, embeddingService);
    }

    /* ---------- multi 多步流程 ---------- */

    private AgentDefinition multiDef() {
        AgentDefinition def = contentDef();
        def.setExecMode("multi");
        def.setSteps("["
                + "{\"stepName\":\"摘要\",\"prompt\":\"请摘要{context}\",\"inputFrom\":\"context\",\"outputKey\":\"summary\"},"
                + "{\"stepName\":\"结论\",\"prompt\":\"基于{summary}给出结论\",\"inputFrom\":\"prev\",\"outputKey\":\"conclusion\"}"
                + "]");
        return def;
    }

    @Test
    void should_execute_multi_steps_and_chain_artifacts() {
        AgentDefinition def = multiDef();
        AgentRun run = new AgentRun();
        run.setId(12L);
        run.setQuestion("q");

        when(llmService.chat(anyString()))
                .thenReturn("摘要输出")
                .thenReturn("结论输出");

        CustomAgentExecutor.ContextInfo ctx = executor.prepareContext(def, contentReq("q"), 1L);
        executor.executeMulti(run, def, ctx);

        assertEquals("COMPLETED", run.getStatus());
        assertEquals("结论输出", run.getResult());
        // steps_result 快照应包含 2 步且均 SUCCESS
        assertTrue(run.getStepsResult().contains("\"status\":\"SUCCESS\""));
        // 每步 RUNNING+SUCCESS 各更新一次，末步后另置 COMPLETED；只断言至少落库过
        verify(runMapper, atLeast(1)).updateById(run);
    }

    @Test
    void should_fail_multi_run_when_a_step_throws() {
        AgentDefinition def = multiDef();
        AgentRun run = new AgentRun();
        run.setId(13L);
        run.setQuestion("q");

        when(llmService.chat(anyString()))
                .thenReturn("摘要输出")
                .thenThrow(new BizException("模型调用失败"));

        CustomAgentExecutor.ContextInfo ctx = executor.prepareContext(def, contentReq("q"), 1L);
        executor.executeMulti(run, def, ctx);

        assertEquals("FAILED", run.getStatus());
        assertTrue(run.getErrorMsg().contains("结论"));
    }
}
