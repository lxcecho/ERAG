/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.kb.governance.lifecycle;

import com.knowledge.common.exception.BizException;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.governance.entity.DocumentVersion;
import com.knowledge.kb.governance.lifecycle.dto.VersionRollbackRequest;
import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleAction;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleStatus;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeAuditService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeLifecycleService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgePolicyService;
import com.knowledge.kb.governance.lifecycle.service.impl.KnowledgeVersionServiceImpl;
import com.knowledge.kb.governance.mapper.DocumentVersionMapper;
import com.knowledge.kb.governance.service.KnowledgeGovernanceService;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识版本服务单元测试。
 * <p>验证：rollback 目标版本不存在抛异常 / 快照恢复+版本自增 / PUBLISHED+requireReview 触发重审 / 不触发重审分支。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeVersionServiceImplTest {

    @Mock
    private KnowledgeGovernanceService governanceService;
    @Mock
    private KbDocumentService kbDocumentService;
    @Mock
    private KbPermissionService kbPermissionService;
    @Mock
    private DocumentVersionMapper versionMapper;
    @Mock
    private KnowledgePolicyService policyService;
    @Mock
    private KnowledgeLifecycleService lifecycleService;
    @Mock
    private KnowledgeAuditService auditService;

    private KnowledgeVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeVersionServiceImpl(
                governanceService, kbDocumentService, kbPermissionService,
                versionMapper, policyService, lifecycleService, auditService);
    }

    private KbDocument publishedDoc(int currentVersion) {
        KbDocument doc = new KbDocument();
        doc.setId(1L);
        doc.setKbId(100L);
        doc.setTenantId(1L);
        doc.setVersion(currentVersion);
        doc.setLifecycleStatus(LifecycleStatus.PUBLISHED.name());
        doc.setStoredName("current.pdf");
        doc.setFilePath("/current");
        doc.setFileSize(1000L);
        doc.setMd5("current-md5");
        return doc;
    }

    private DocumentVersion snapshot(int version) {
        DocumentVersion v = new DocumentVersion();
        v.setDocId(1L);
        v.setVersion(version);
        v.setStoredName("v" + version + ".pdf");
        v.setFilePath("/v" + version);
        v.setFileSize(2000L);
        v.setMd5("v" + version + "-md5");
        return v;
    }

    private KnowledgePolicy policy(boolean requireReview) {
        KnowledgePolicy p = new KnowledgePolicy();
        p.setKbId(100L);
        p.setRequireReview(requireReview);
        return p;
    }

    private VersionRollbackRequest rollbackTo(int version) {
        VersionRollbackRequest req = new VersionRollbackRequest();
        req.setDocId(1L);
        req.setVersion(version);
        return req;
    }

    @Test
    void 回滚_目标版本不存在_抛异常() {
        when(kbDocumentService.getById(1L)).thenReturn(publishedDoc(3));
        when(versionMapper.selectOne(any())).thenReturn(null);

        assertThrows(BizException.class, () -> service.rollback(rollbackTo(99), 9L));
        verify(kbDocumentService, never()).updateById(any(KbDocument.class));
    }

    @Test
    void 回滚_快照恢复且版本号自增() {
        KbDocument doc = publishedDoc(3);
        when(kbDocumentService.getById(1L)).thenReturn(doc);
        DocumentVersion target = snapshot(1);
        when(versionMapper.selectOne(any())).thenReturn(target);

        service.rollback(rollbackTo(1), 9L);

        // 快照写回主表
        assertEquals("v1.pdf", doc.getStoredName());
        assertEquals("/v1", doc.getFilePath());
        assertEquals(2000L, doc.getFileSize());
        assertEquals("v1-md5", doc.getMd5());
        // 版本号自增：3 -> 4
        assertEquals(4, doc.getVersion());
        verify(kbDocumentService, times(1)).updateById(doc);

        // 插入新版本快照（版本号=4）
        ArgumentCaptor<DocumentVersion> captor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versionMapper, times(1)).insert(captor.capture());
        assertEquals(4, captor.getValue().getVersion());
        assertEquals("回滚至版本 1", captor.getValue().getChangeLog());
        assertEquals("v1.pdf", captor.getValue().getStoredName());
    }

    @Test
    void 回滚_PUBLISHED且强制审核_触发UPDATE重审() {
        KbDocument doc = publishedDoc(3);
        when(kbDocumentService.getById(1L)).thenReturn(doc);
        when(versionMapper.selectOne(any())).thenReturn(snapshot(1));
        when(policyService.getByKb(100L)).thenReturn(policy(true));

        service.rollback(rollbackTo(1), 9L);

        // 触发生命周期 UPDATE 迁移
        verify(lifecycleService, times(1)).transition(eq(1L),
                eq(LifecycleAction.UPDATE), eq(9L), eq("版本回滚触发重审"));
        // 记 VERSION_ROLLBACK 审计
        verify(auditService, times(1)).record(eq(1L), eq(9L), eq(1L),
                eq(KnowledgeAuditService.ACTION_VERSION_ROLLBACK), anyBoolean(), any(), any());
    }

    @Test
    void 回滚_PUBLISHED但免审核_不触发重审() {
        KbDocument doc = publishedDoc(3);
        when(kbDocumentService.getById(1L)).thenReturn(doc);
        when(versionMapper.selectOne(any())).thenReturn(snapshot(1));
        when(policyService.getByKb(100L)).thenReturn(policy(false));

        service.rollback(rollbackTo(1), 9L);

        verify(lifecycleService, never()).transition(anyLong(), any(), anyLong(), any());
        verify(auditService, times(1)).record(eq(1L), eq(9L), eq(1L),
                eq(KnowledgeAuditService.ACTION_VERSION_ROLLBACK), anyBoolean(), any(), any());
    }

    @Test
    void 回滚_非PUBLISHED状态_不触发重审() {
        KbDocument doc = publishedDoc(3);
        doc.setLifecycleStatus(LifecycleStatus.DRAFT.name());
        when(kbDocumentService.getById(1L)).thenReturn(doc);
        when(versionMapper.selectOne(any())).thenReturn(snapshot(1));
        when(policyService.getByKb(100L)).thenReturn(policy(true));

        service.rollback(rollbackTo(1), 9L);

        verify(lifecycleService, never()).transition(anyLong(), any(), anyLong(), any());
    }

    @Test
    void 回滚_文档不存在_抛404() {
        when(kbDocumentService.getById(1L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> service.rollback(rollbackTo(1), 9L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void 版本历史与记录版本_透传给governanceService() {
        service.versionPage(null);
        verify(governanceService, times(1)).versionPage(any());

        service.recordVersion(1L, "变更说明", 9L);
        verify(governanceService, times(1)).recordVersion(eq(1L), eq("变更说明"), eq(9L));
    }
}
