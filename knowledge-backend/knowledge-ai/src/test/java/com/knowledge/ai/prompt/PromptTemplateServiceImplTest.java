/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.prompt;

import com.knowledge.ai.calllog.service.AiCallLogger;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.prompt.dto.PromptTemplateRequest;
import com.knowledge.ai.prompt.dto.PromptTemplateVO;
import com.knowledge.ai.prompt.entity.PromptTemplate;
import com.knowledge.ai.prompt.entity.PromptVariable;
import com.knowledge.ai.prompt.entity.PromptVersion;
import com.knowledge.ai.prompt.mapper.PromptTemplateMapper;
import com.knowledge.ai.prompt.mapper.PromptVariableMapper;
import com.knowledge.ai.prompt.mapper.PromptVersionMapper;
import com.knowledge.ai.prompt.service.impl.PromptTemplateServiceImpl;
import com.knowledge.common.context.TenantContext;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prompt 模板服务三表协作单测。
 * <p>风格：plain JUnit5 + Mockito mock()，SUT 用 new 构造传 mock，
 * baseMapper（ServiceImpl 继承字段）通过 ReflectionTestUtils 注入。
 */
class PromptTemplateServiceImplTest {

    private PromptTemplateMapper templateMapper;
    private PromptVersionMapper versionMapper;
    private PromptVariableMapper variableMapper;
    private AiProperties aiProperties;
    private ChatModel chatModel;
    private AiCallLogger aiCallLogger;
    private PromptTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(PromptTemplateMapper.class);
        versionMapper = mock(PromptVersionMapper.class);
        variableMapper = mock(PromptVariableMapper.class);
        aiProperties = new AiProperties();
        chatModel = mock(ChatModel.class);
        aiCallLogger = mock(AiCallLogger.class);
        service = new PromptTemplateServiceImpl(versionMapper, variableMapper, aiProperties, chatModel, aiCallLogger);
        ReflectionTestUtils.setField(service, "baseMapper", templateMapper);
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 模拟 baseMapper.insert 给 template 分配 ID（雪花模拟） */
    private void stubTemplateInsert(Long assignedId) {
        when(templateMapper.insert(any(PromptTemplate.class))).thenAnswer(inv -> {
            PromptTemplate t = inv.getArgument(0);
            t.setId(assignedId);
            return 1;
        });
    }

    /** 模拟 versionMapper.insert 给 version 分配 ID */
    private void stubVersionInsert(Long assignedId) {
        when(versionMapper.insert(any(PromptVersion.class))).thenAnswer(inv -> {
            PromptVersion v = inv.getArgument(0);
            v.setId(assignedId);
            return 1;
        });
    }

    private PromptTemplateRequest req(String code, String name, String type, String content, String variables) {
        PromptTemplateRequest r = new PromptTemplateRequest();
        r.setPromptCode(code);
        r.setName(name);
        r.setType(type);
        r.setContent(content);
        r.setVariables(variables);
        r.setRemark("测试版本");
        return r;
    }

    private PromptTemplateVO vo(Long id, Long templateId, Long tenantId, String code, String name,
                                 String type, int version, String content, String status) {
        PromptTemplateVO v = new PromptTemplateVO();
        v.setId(id);
        v.setTemplateId(templateId);
        v.setTenantId(tenantId);
        v.setPromptCode(code);
        v.setName(name);
        v.setType(type);
        v.setVersion(version);
        v.setContent(content);
        v.setStatus(status);
        return v;
    }

    // ==================== create ====================

    @Test
    void create_新建模板_写入三表() {
        when(templateMapper.findByCode(1L, "test_prompt")).thenReturn(null);
        stubTemplateInsert(2001L);
        stubVersionInsert(1001L);

        Long versionId = service.create(req("test_prompt", "测试", "rag", "内容{ctx}", "ctx,question"), 99L);

        assertEquals(1001L, versionId);
        verify(templateMapper).insert(any(PromptTemplate.class));
        verify(versionMapper).insert(any(PromptVersion.class));
        // 两个变量 → 两次 insert
        verify(variableMapper, times(2)).insert(any(PromptVariable.class));
        verify(variableMapper).deleteByTemplateId(2001L);
    }

    @Test
    void create_重复code_抛异常() {
        when(templateMapper.findByCode(1L, "test_prompt")).thenReturn(new PromptTemplate());
        assertThrows(RuntimeException.class,
                () -> service.create(req("test_prompt", "测试", "rag", "内容", ""), 99L));
        verify(templateMapper, never()).insert(any(PromptTemplate.class));
    }

    @Test
    void create_变量为空_仅清空不插入() {
        when(templateMapper.findByCode(1L, "no_var")).thenReturn(null);
        stubTemplateInsert(2002L);
        stubVersionInsert(1002L);

        service.create(req("no_var", "无变量", "system", "内容", ""), 99L);

        verify(variableMapper).deleteByTemplateId(2002L);
        verify(variableMapper, never()).insert(any(PromptVariable.class));
    }

    // ==================== edit ====================

    @Test
    void edit_DRAFT版本_原地更新() {
        PromptTemplateVO target = vo(1001L, 2001L, 1L, "test", "旧名", "rag", 1, "旧内容", "DRAFT");
        when(templateMapper.findVoByIdVisible(1L, 1001L)).thenReturn(target);
        PromptVersion version = new PromptVersion();
        version.setId(1001L);
        version.setContent("旧内容");
        when(versionMapper.selectById(1001L)).thenReturn(version);
        PromptTemplate template = new PromptTemplate();
        template.setId(2001L);
        when(templateMapper.selectById(2001L)).thenReturn(template);

        PromptTemplateRequest request = req("test", "新名", "rag", "新内容", "a,b");
        request.setId(1001L);
        Long resultId = service.edit(request, 99L);

        assertEquals(1001L, resultId);
        verify(versionMapper).updateById(any(PromptVersion.class));
        verify(templateMapper).updateById(any(PromptTemplate.class));
        verify(variableMapper).deleteByTemplateId(2001L);
        verify(variableMapper, times(2)).insert(any(PromptVariable.class));
        verify(versionMapper, never()).insert(any(PromptVersion.class));
    }

    @Test
    void edit_PUBLISHED平台预置_派生新DRAFT() {
        PromptTemplateVO target = vo(1001L, 2001L, 0L, "test", "平台模板", "rag", 1, "平台内容", "PUBLISHED");
        when(templateMapper.findVoByIdVisible(1L, 1001L)).thenReturn(target);
        // 租户 1 尚无自有 template
        when(templateMapper.findByCode(1L, "test")).thenReturn(null);
        stubTemplateInsert(3001L);
        stubVersionInsert(5001L);
        when(versionMapper.maxVersionByTemplateId(3001L)).thenReturn(0);

        PromptTemplateRequest request = req("test", "租户派生", "rag", "派生内容", "ctx");
        request.setId(1001L);
        Long resultId = service.edit(request, 99L);

        assertEquals(5001L, resultId);
        // 新建租户 template + 新 DRAFT version
        verify(templateMapper).insert(any(PromptTemplate.class));
        verify(versionMapper).insert(any(PromptVersion.class));
        verify(variableMapper).deleteByTemplateId(3001L);
        verify(variableMapper, times(1)).insert(any(PromptVariable.class));
    }

    // ==================== publish ====================

    @Test
    void publish_旧发布版归档() {
        PromptTemplateVO target = vo(1001L, 2001L, 1L, "test", "测试", "rag", 2, "内容", "DRAFT");
        when(templateMapper.findVoByIdVisible(1L, 1001L)).thenReturn(target);
        PromptVersion version = new PromptVersion();
        version.setId(1001L);
        version.setStatus("DRAFT");
        when(versionMapper.selectById(1001L)).thenReturn(version);

        service.publish(1001L);

        verify(versionMapper).archivePublished(2001L);
        verify(versionMapper).updateById(any(PromptVersion.class));
    }

    @Test
    void publish_平台预置_抛异常() {
        PromptTemplateVO target = vo(1001L, 2001L, 0L, "test", "平台", "rag", 1, "内容", "DRAFT");
        when(templateMapper.findVoByIdVisible(1L, 1001L)).thenReturn(target);
        assertThrows(RuntimeException.class, () -> service.publish(1001L));
        verify(versionMapper, never()).archivePublished(any());
    }

    // ==================== rollback ====================

    @Test
    void rollback_派生新发布版() {
        PromptTemplateVO target = vo(1001L, 2001L, 1L, "test", "测试", "rag", 1, "历史内容", "ARCHIVED");
        when(templateMapper.findVoByIdVisible(1L, 1001L)).thenReturn(target);
        PromptTemplate tenantTemplate = new PromptTemplate();
        tenantTemplate.setId(2001L);
        when(templateMapper.findByCode(1L, "test")).thenReturn(tenantTemplate);
        when(versionMapper.maxVersionByTemplateId(2001L)).thenReturn(2);
        stubVersionInsert(5001L);

        service.rollback(1001L, 99L);

        // 先归档旧 PUBLISHED，再 insert 新 PUBLISHED（version=3）
        verify(versionMapper).archivePublished(2001L);
        verify(versionMapper).insert(any(PromptVersion.class));
    }

    // ==================== resolveRagSystemPrompt ====================

    @Test
    void resolveRagSystemPrompt_未启用DB模板_回退内置() {
        aiProperties.getRag().setUseDbTemplate(false);
        String result = service.resolveRagSystemPrompt("检索上下文");
        // 内置模板含 {context} 占位符，应被替换
        assertEquals(true, result.contains("检索上下文"));
        verify(templateMapper, never()).getPublishedByCode(any(), any());
    }

    @Test
    void resolveRagSystemPrompt_启用DB模板_取已发布内容渲染() {
        aiProperties.getRag().setUseDbTemplate(true);
        PromptTemplateVO published = vo(1001L, 2001L, 0L, "rag_system_prompt", "RAG", "rag", 1,
                "DB模板:{context}", "PUBLISHED");
        when(templateMapper.getPublishedByCode(1L, "rag_system_prompt")).thenReturn(published);

        String result = service.resolveRagSystemPrompt("知识片段");
        assertEquals("DB模板:知识片段", result);
    }

    @Test
    void resolveRagSystemPrompt_启用DB模板但无发布_回退内置() {
        aiProperties.getRag().setUseDbTemplate(true);
        when(templateMapper.getPublishedByCode(1L, "rag_system_prompt")).thenReturn(null);

        String result = service.resolveRagSystemPrompt("上下文");
        // 回退内置模板，应包含上下文
        assertEquals(true, result.contains("上下文"));
    }
}
