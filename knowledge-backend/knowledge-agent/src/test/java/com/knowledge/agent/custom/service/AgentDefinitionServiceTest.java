/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.custom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.custom.dto.AgentDefinitionRequest;
import com.knowledge.agent.custom.entity.AgentDefinition;
import com.knowledge.agent.custom.mapper.AgentDefinitionMapper;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.service.KbPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentDefinitionService} 单元测试：CRUD、权限（非创建者操作 403）、发布回落、多步校验、软删。
 */
@ExtendWith(MockitoExtension.class)
class AgentDefinitionServiceTest {

    @Mock
    private AgentDefinitionMapper definitionMapper;

    @Mock
    private KbPermissionService kbPermissionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AgentDefinitionService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AgentDefinitionRequest baseReq(String sourceMode, String execMode) {
        AgentDefinitionRequest req = new AgentDefinitionRequest();
        req.setName("测试助手");
        req.setDescription("描述");
        req.setSystemPrompt("请基于 {context} 分析 {question}");
        req.setSourceMode(sourceMode);
        req.setExecMode(execMode);
        return req;
    }

    /* ---------- create ---------- */

    @Test
    void should_create_definition_as_draft() {
        AgentDefinitionRequest req = baseReq("content", "single");
        when(definitionMapper.insert(any(AgentDefinition.class))).thenAnswer(inv -> {
            AgentDefinition def = inv.getArgument(0);
            def.setId(99L);
            return 1;
        });

        Long id = service.create(req, 1L);

        assertEquals(99L, id);
        ArgumentCaptor<AgentDefinition> captor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(definitionMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getTenantId());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals("DRAFT", captor.getValue().getStatus());
    }

    @Test
    void should_reject_invalid_source_mode() {
        AgentDefinitionRequest req = baseReq("invalid", "single");
        assertThrows(BizException.class, () -> service.create(req, 1L));
        verify(definitionMapper, never()).insert(any(AgentDefinition.class));
    }

    @Test
    void should_allow_kb_mode_without_kb_id() {
        // kb 模式 kbId 可选：对话框检索时可随时切换任意有权限的知识库
        when(definitionMapper.insert(any(AgentDefinition.class))).thenReturn(1);
        AgentDefinitionRequest req = baseReq("kb", "single");
        assertDoesNotThrow(() -> service.create(req, 1L));
        // 未绑定 kbId 时不触发权限校验
        verify(kbPermissionService, never()).checkViewer(any(), any());
    }

    @Test
    void should_default_source_mode_to_kb_when_blank() {
        // sourceMode 缺省 → 默认 kb（运行时由对话框 inputType 覆盖）
        when(definitionMapper.insert(any(AgentDefinition.class))).thenReturn(1);
        AgentDefinitionRequest req = baseReq("", "single");
        assertDoesNotThrow(() -> service.create(req, 1L));
    }

    @Test
    void should_reject_multi_mode_without_steps() {
        AgentDefinitionRequest req = baseReq("content", "multi");
        assertThrows(BizException.class, () -> service.create(req, 1L));
    }

    @Test
    void should_reject_too_many_steps() {
        AgentDefinitionRequest req = baseReq("content", "multi");
        StringBuilder json = new StringBuilder("[");
        for (int i = 1; i <= 6; i++) {
            if (i > 1) json.append(",");
            json.append("{\"stepName\":\"步骤").append(i)
                    .append("\",\"prompt\":\"分析{context}\",\"inputFrom\":\"context\",\"outputKey\":\"k").append(i).append("\"}");
        }
        json.append("]");
        req.setSteps(json.toString());
        assertThrows(BizException.class, () -> service.create(req, 1L));
    }

    @Test
    void should_reject_duplicate_output_key() {
        AgentDefinitionRequest req = baseReq("content", "multi");
        req.setSteps("[{\"stepName\":\"a\",\"prompt\":\"p1\",\"outputKey\":\"same\"},"
                + "{\"stepName\":\"b\",\"prompt\":\"p2\",\"outputKey\":\"same\"}]");
        assertThrows(BizException.class, () -> service.create(req, 1L));
    }

    /* ---------- edit / publish / delete ---------- */

    @Test
    void should_reject_edit_by_non_owner() {
        AgentDefinition def = new AgentDefinition();
        def.setId(1L);
        def.setUserId(2L);
        when(definitionMapper.selectById(1L)).thenReturn(def);

        AgentDefinitionRequest req = baseReq("content", "single");
        req.setId(1L);

        BizException ex = assertThrows(BizException.class, () -> service.edit(req, 1L));
        assertEquals(403, ex.getCode());
    }

    @Test
    void should_fall_back_to_draft_when_editing_published() {
        AgentDefinition def = new AgentDefinition();
        def.setId(1L);
        def.setUserId(1L);
        def.setStatus("PUBLISHED");
        when(definitionMapper.selectById(1L)).thenReturn(def);

        AgentDefinitionRequest req = baseReq("content", "single");
        req.setId(1L);

        service.edit(req, 1L);

        ArgumentCaptor<AgentDefinition> captor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(definitionMapper).updateById(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus());
        assertEquals("测试助手", captor.getValue().getName());
    }

    @Test
    void should_publish_owned_definition() {
        AgentDefinition def = new AgentDefinition();
        def.setId(1L);
        def.setUserId(1L);
        def.setStatus("DRAFT");
        when(definitionMapper.selectById(1L)).thenReturn(def);

        service.publish(1L, 1L);

        ArgumentCaptor<AgentDefinition> captor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(definitionMapper).updateById(captor.capture());
        assertEquals("PUBLISHED", captor.getValue().getStatus());
    }

    @Test
    void should_soft_delete_owned_definition() {
        AgentDefinition def = new AgentDefinition();
        def.setId(1L);
        def.setUserId(1L);
        when(definitionMapper.selectById(1L)).thenReturn(def);

        service.delete(1L, 1L);

        verify(definitionMapper).deleteById(1L);
    }

    @Test
    void should_reject_delete_by_non_owner() {
        AgentDefinition def = new AgentDefinition();
        def.setId(1L);
        def.setUserId(2L);
        when(definitionMapper.selectById(1L)).thenReturn(def);

        BizException ex = assertThrows(BizException.class, () -> service.delete(1L, 1L));
        assertEquals(403, ex.getCode());
        verify(definitionMapper, never()).deleteById(any());
    }

    @Test
    void should_return_null_name_for_missing_definition() {
        when(definitionMapper.selectById(999L)).thenReturn(null);
        assertNull(service.getByIdQuiet(999L));
    }
}
