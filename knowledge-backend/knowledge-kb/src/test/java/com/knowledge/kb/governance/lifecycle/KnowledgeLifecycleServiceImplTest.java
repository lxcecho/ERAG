/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.kb.governance.lifecycle;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.governance.entity.DocumentReview;
import com.knowledge.kb.governance.enums.ReviewStatus;
import com.knowledge.kb.governance.lifecycle.dto.LifecycleVo;
import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleAction;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleStatus;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeAuditService;
import com.knowledge.kb.governance.lifecycle.service.KnowledgePolicyService;
import com.knowledge.kb.governance.lifecycle.service.impl.KnowledgeLifecycleServiceImpl;
import com.knowledge.kb.governance.lifecycle.statemachine.LifecycleStateMachine;
import com.knowledge.kb.governance.mapper.DocumentFingerprintMapper;
import com.knowledge.kb.governance.mapper.DocumentQualityMapper;
import com.knowledge.kb.governance.mapper.DocumentReviewMapper;
import com.knowledge.kb.governance.mapper.DocumentVersionMapper;
import com.knowledge.kb.mapper.KbDocAclMapper;
import com.knowledge.kb.mapper.KbDocAuditLogMapper;
import com.knowledge.kb.mapper.KbDocumentMapper;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * 生命周期服务单元测试。
 * <p>验证：transition 桥接同步 review_status / 非法流转抛异常 / 写审核流水与审计 / autoArchive / purgeRetained 级联清理。
 * <p>说明：{@link LifecycleStateMachine} 为无依赖纯函数组件，测试中以真实实例注入，不 mock。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeLifecycleServiceImplTest {

    @Mock
    private KbDocumentService kbDocumentService;
    @Mock
    private KbDocumentMapper kbDocumentMapper;
    @Mock
    private KbPermissionService kbPermissionService;
    @Mock
    private KnowledgePolicyService policyService;
    @Mock
    private KnowledgeAuditService auditService;
    @Mock
    private DocumentVersionMapper versionMapper;
    @Mock
    private DocumentFingerprintMapper fingerprintMapper;
    @Mock
    private DocumentQualityMapper qualityMapper;
    @Mock
    private DocumentReviewMapper reviewMapper;
    @Mock
    private KbDocAclMapper aclMapper;
    @Mock
    private KbDocAuditLogMapper auditLogMapper;

    private KnowledgeLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeLifecycleServiceImpl(
                kbDocumentService, kbDocumentMapper, kbPermissionService,
                policyService, auditService, new LifecycleStateMachine(),
                versionMapper, fingerprintMapper, qualityMapper, reviewMapper,
                aclMapper, auditLogMapper);
    }

    private KbDocument doc(Long id, String lifecycleStatus) {
        KbDocument d = new KbDocument();
        d.setId(id);
        d.setKbId(100L);
        d.setTenantId(1L);
        d.setLifecycleStatus(lifecycleStatus);
        d.setVersion(1);
        return d;
    }

    private KnowledgePolicy policy(boolean requireReview) {
        KnowledgePolicy p = new KnowledgePolicy();
        p.setKbId(100L);
        p.setRequireReview(requireReview);
        return p;
    }

    /* ==================== transition ==================== */

    @Test
    void 提交审核_桥接同步review状态为PENDING并写流水() {
        KbDocument doc = doc(1L, LifecycleStatus.DRAFT.name());
        when(kbDocumentService.getById(1L)).thenReturn(doc);
        when(policyService.getByKb(100L)).thenReturn(policy(true));

        service.transition(1L, LifecycleAction.SUBMIT, 9L, "提交审核");

        assertEquals(LifecycleStatus.REVIEW.name(), doc.getLifecycleStatus());
        assertEquals(ReviewStatus.PENDING.name(), doc.getReviewStatus(), "桥接应同步 review_status=PENDING");
        verify(kbDocumentService).updateById(doc);
        // REVIEW 阶段写一条 document_review 流水
        verify(reviewMapper, times(1)).insert(any(DocumentReview.class));
        // 写治理审计
        verify(auditService, times(1)).record(eq(1L), eq(9L), eq(1L),
                eq(KnowledgeAuditService.ACTION_LIFECYCLE_SUBMIT), anyBoolean(), any(), any());
    }

    @Test
    void 审核通过_桥接同步review状态为APPROVED并记审核人() {
        KbDocument doc = doc(1L, LifecycleStatus.REVIEW.name());
        when(kbDocumentService.getById(1L)).thenReturn(doc);
        when(policyService.getByKb(100L)).thenReturn(policy(true));

        service.transition(1L, LifecycleAction.APPROVE, 9L, "通过");

        assertEquals(LifecycleStatus.PUBLISHED.name(), doc.getLifecycleStatus());
        assertEquals(ReviewStatus.APPROVED.name(), doc.getReviewStatus(), "桥接应同步 review_status=APPROVED");
        assertEquals(9L, doc.getReviewerId(), "桥接应记审核人");
        assertNotNull(doc.getReviewedAt());
        verify(reviewMapper, times(1)).insert(any(DocumentReview.class));
        verify(auditService, times(1)).record(eq(1L), eq(9L), eq(1L),
                eq(KnowledgeAuditService.ACTION_LIFECYCLE_APPROVE), anyBoolean(), any(), any());
    }

    @Test
    void 归档_桥接同步归档时间且不写review流水() {
        KbDocument doc = doc(1L, LifecycleStatus.PUBLISHED.name());
        when(kbDocumentService.getById(1L)).thenReturn(doc);
        when(policyService.getByKb(100L)).thenReturn(policy(true));

        service.transition(1L, LifecycleAction.ARCHIVE, 9L, "归档");

        assertEquals(LifecycleStatus.ARCHIVED.name(), doc.getLifecycleStatus());
        assertNotNull(doc.getArchivedAt(), "桥接应记归档时间");
        // ARCHIVE 不在 REVIEW 阶段，不写 document_review 流水
        verify(reviewMapper, never()).insert(any(DocumentReview.class));
        verify(auditService, times(1)).record(eq(1L), eq(9L), eq(1L),
                eq(KnowledgeAuditService.ACTION_ARCHIVE), anyBoolean(), any(), any());
    }

    @Test
    void 非法流转_抛BizException且不更新文档() {
        KbDocument doc = doc(1L, LifecycleStatus.DRAFT.name());
        when(kbDocumentService.getById(1L)).thenReturn(doc);
        when(policyService.getByKb(100L)).thenReturn(policy(true));

        // DRAFT + APPROVE 非法（需先 SUBMIT 到 REVIEW）
        assertThrows(BizException.class,
                () -> service.transition(1L, LifecycleAction.APPROVE, 9L, "非法"));

        verify(kbDocumentService, never()).updateById(any(KbDocument.class));
        verify(auditService, never()).record(anyLong(), anyLong(), anyLong(),
                any(), anyBoolean(), any(), any());
    }

    @Test
    void 文档不存在_抛404异常() {
        when(kbDocumentService.getById(1L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> service.transition(1L, LifecycleAction.SUBMIT, 9L, null));
        assertEquals(404, ex.getCode());
    }

    /* ==================== getState ==================== */

    @Test
    void getState_返回完整生命周期视图() {
        KbDocument doc = doc(1L, LifecycleStatus.PUBLISHED.name());
        doc.setReviewStatus(ReviewStatus.APPROVED.name());
        doc.setReviewerId(9L);
        doc.setOriginalName("测试文档.pdf");
        when(kbDocumentService.getById(1L)).thenReturn(doc);

        LifecycleVo vo = service.getState(1L);
        assertEquals(1L, vo.getDocId());
        assertEquals(100L, vo.getKbId());
        assertEquals("测试文档.pdf", vo.getDocName());
        assertEquals(LifecycleStatus.PUBLISHED.name(), vo.getLifecycleStatus());
        assertEquals(ReviewStatus.APPROVED.name(), vo.getReviewStatus());
        assertEquals(9L, vo.getReviewerId());
    }

    /* ==================== autoArchive ==================== */

    @Test
    void autoArchive_发布超期_归档并写审计() {
        KbDocument doc = doc(1L, LifecycleStatus.PUBLISHED.name());
        // 发布时间 = 30天前，策略 autoArchiveDays=7 → 应归档
        doc.setReviewedAt(LocalDateTime.now().minusDays(30));
        when(kbDocumentService.list(any(Wrapper.class))).thenReturn(List.of(doc));
        KnowledgePolicy p = policy(true);
        p.setAutoArchiveDays(7);
        when(policyService.getByKb(100L)).thenReturn(p);

        int count = service.autoArchive();

        assertEquals(1, count);
        assertEquals(LifecycleStatus.ARCHIVED.name(), doc.getLifecycleStatus());
        assertNotNull(doc.getArchivedAt());
        verify(auditService, times(1)).record(eq(1L), eq(0L), eq(1L),
                eq(KnowledgeAuditService.ACTION_ARCHIVE), anyBoolean(), any(), any());
    }

    @Test
    void autoArchive_未超期_不归档() {
        KbDocument doc = doc(1L, LifecycleStatus.PUBLISHED.name());
        doc.setReviewedAt(LocalDateTime.now().minusDays(2));
        when(kbDocumentService.list(any(Wrapper.class))).thenReturn(List.of(doc));
        KnowledgePolicy p = policy(true);
        p.setAutoArchiveDays(7);
        when(policyService.getByKb(100L)).thenReturn(p);

        int count = service.autoArchive();

        assertEquals(0, count);
        verify(kbDocumentService, never()).updateById(any(KbDocument.class));
        verify(auditService, never()).record(anyLong(), anyLong(), anyLong(),
                any(), anyBoolean(), any(), any());
    }

    @Test
    void autoArchive_策略无autoArchiveDays_跳过() {
        KbDocument doc = doc(1L, LifecycleStatus.PUBLISHED.name());
        when(kbDocumentService.list(any(Wrapper.class))).thenReturn(List.of(doc));
        when(policyService.getByKb(100L)).thenReturn(policy(true)); // autoArchiveDays=null

        int count = service.autoArchive();

        assertEquals(0, count);
        verify(kbDocumentService, never()).updateById(any(KbDocument.class));
    }

    /* ==================== purgeRetained ==================== */

    @Test
    void purgeRetained_保留期到期_物理删主表并级联清理() {
        KbDocument doc = doc(1L, LifecycleStatus.ARCHIVED.name());
        // 归档时间 = 90天前，策略 retentionDays=30 → 应硬删
        doc.setArchivedAt(LocalDateTime.now().minusDays(90));
        when(kbDocumentService.list(any(Wrapper.class))).thenReturn(List.of(doc));
        KnowledgePolicy p = policy(true);
        p.setRetentionDays(30);
        when(policyService.getByKb(100L)).thenReturn(p);

        int count = service.purgeRetained();

        assertEquals(1, count);
        // 删前写 RETENTION_PURGE 审计
        verify(auditService, times(1)).record(eq(1L), eq(0L), eq(1L),
                eq(KnowledgeAuditService.ACTION_RETENTION_PURGE), anyBoolean(), any(), any());
        // 级联清理 6 张表
        verify(versionMapper, times(1)).delete(any());
        verify(fingerprintMapper, times(1)).delete(any());
        verify(qualityMapper, times(1)).delete(any());
        verify(reviewMapper, times(1)).delete(any());
        verify(aclMapper, times(1)).delete(any());
        verify(auditLogMapper, times(1)).delete(any());
        // 物理删主表
        verify(kbDocumentMapper, times(1)).physicalDeleteById(eq(1L));
    }

    @Test
    void purgeRetained_保留期未到期_不删() {
        KbDocument doc = doc(1L, LifecycleStatus.ARCHIVED.name());
        doc.setArchivedAt(LocalDateTime.now().minusDays(10));
        when(kbDocumentService.list(any(Wrapper.class))).thenReturn(List.of(doc));
        KnowledgePolicy p = policy(true);
        p.setRetentionDays(30);
        when(policyService.getByKb(100L)).thenReturn(p);

        int count = service.purgeRetained();

        assertEquals(0, count);
        verify(kbDocumentMapper, never()).physicalDeleteById(anyLong());
    }

    @Test
    void purgeRetained_策略无retentionDays_跳过() {
        KbDocument doc = doc(1L, LifecycleStatus.ARCHIVED.name());
        doc.setArchivedAt(LocalDateTime.now().minusDays(90));
        when(kbDocumentService.list(any(Wrapper.class))).thenReturn(List.of(doc));
        when(policyService.getByKb(100L)).thenReturn(policy(true)); // retentionDays=null

        int count = service.purgeRetained();

        assertEquals(0, count);
        verify(kbDocumentMapper, never()).physicalDeleteById(anyLong());
    }
}
