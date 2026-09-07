/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.kb.governance.lifecycle;

import com.knowledge.common.context.TenantContext;
import com.knowledge.kb.governance.lifecycle.dto.PolicyRequest;
import com.knowledge.kb.governance.lifecycle.dto.PolicyVo;
import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;
import com.knowledge.kb.governance.lifecycle.mapper.KnowledgePolicyMapper;
import com.knowledge.kb.governance.lifecycle.service.KnowledgeAuditService;
import com.knowledge.kb.governance.lifecycle.service.impl.KnowledgePolicyServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识治理策略服务单元测试。
 * <p>验证：getByKb 默认回退不落库 / saveOrUpdate 新增与更新分支 / 写操作记 POLICY_UPDATE 审计。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgePolicyServiceImplTest {

    @Mock
    private KnowledgePolicyMapper policyMapper;
    @Mock
    private KnowledgeAuditService auditService;

    @InjectMocks
    private KnowledgePolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getByKb_无记录_返回默认策略不落库() {
        when(policyMapper.selectOne(any())).thenReturn(null);
        KnowledgePolicy policy = service.getByKb(100L);
        assertNotNull(policy);
        assertTrue(policy.getRequireReview(), "默认策略应强制审核");
        assertNull(policy.getAutoArchiveDays(), "默认策略 autoArchiveDays 应为 null");
        assertNull(policy.getRetentionDays(), "默认策略 retentionDays 应为 null");
        assertEquals(100L, policy.getKbId());
    }

    @Test
    void getByKb_有记录_返回库中策略() {
        KnowledgePolicy db = new KnowledgePolicy();
        db.setKbId(100L);
        db.setRequireReview(false);
        db.setAutoArchiveDays(30);
        when(policyMapper.selectOne(any())).thenReturn(db);

        KnowledgePolicy policy = service.getByKb(100L);
        assertEquals(false, policy.getRequireReview());
        assertEquals(30, policy.getAutoArchiveDays());
    }

    @Test
    void saveOrUpdate_新增_插入并记审计() {
        when(policyMapper.selectOne(any())).thenReturn(null);
        PolicyRequest request = new PolicyRequest();
        request.setKbId(100L);
        request.setRequireReview(false);
        request.setAutoArchiveDays(30);
        request.setRetentionDays(90);

        service.saveOrUpdate(request, 9L);

        ArgumentCaptor<KnowledgePolicy> captor = ArgumentCaptor.forClass(KnowledgePolicy.class);
        verify(policyMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getKbId());
        assertEquals(false, captor.getValue().getRequireReview());
        verify(policyMapper, never()).updateById(any(KnowledgePolicy.class));
        verify(auditService, times(1)).record(eq(1L), eq(9L), eq(null),
                eq(KnowledgeAuditService.ACTION_POLICY_UPDATE), anyBoolean(), any(), any());
    }

    @Test
    void saveOrUpdate_已存在_更新并记审计() {
        KnowledgePolicy existing = new KnowledgePolicy();
        existing.setId(1L);
        existing.setKbId(100L);
        existing.setRequireReview(true);
        when(policyMapper.selectOne(any())).thenReturn(existing);

        PolicyRequest request = new PolicyRequest();
        request.setKbId(100L);
        request.setRequireReview(false);
        request.setAutoArchiveDays(60);

        service.saveOrUpdate(request, 9L);

        verify(policyMapper).updateById(any(KnowledgePolicy.class));
        verify(policyMapper, never()).insert(any(KnowledgePolicy.class));
        verify(auditService, times(1)).record(eq(1L), eq(9L), eq(null),
                eq(KnowledgeAuditService.ACTION_POLICY_UPDATE), anyBoolean(), any(), any());
    }

    @Test
    void deleteByKb_删除并记审计() {
        when(policyMapper.delete(any())).thenReturn(1);
        service.deleteByKb(100L, 9L);
        verify(policyMapper).delete(any());
        verify(auditService, times(1)).record(eq(1L), eq(9L), eq(null),
                eq(KnowledgeAuditService.ACTION_POLICY_UPDATE), anyBoolean(), any(), any());
    }

    @Test
    void getVoByKb_无记录_返回默认策略视图id为null() {
        when(policyMapper.selectOne(any())).thenReturn(null);
        PolicyVo vo = service.getVoByKb(100L);
        assertNotNull(vo);
        assertNull(vo.getId(), "默认策略视图 id 应为 null");
        assertTrue(vo.getRequireReview());
    }
}
