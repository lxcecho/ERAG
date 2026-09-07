package com.knowledge.kb.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.constant.KbConstants;
import com.knowledge.kb.dto.KbDocumentQuery;
import com.knowledge.kb.dto.KbDocumentVo;
import com.knowledge.kb.dto.UploadResultVo;
import com.knowledge.kb.entity.KbDocAcl;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.entity.KbParseTask;
import com.knowledge.kb.entity.KnowledgeBase;
import com.knowledge.kb.event.DocumentDeletedEvent;
import com.knowledge.kb.mapper.KbDocumentMapper;
import com.knowledge.kb.permission.AclSubjectType;
import com.knowledge.kb.permission.DocPermission;
import com.knowledge.kb.permission.service.DocVisibleSetService;
import com.knowledge.kb.service.KbDocAclService;
import com.knowledge.kb.service.KbDocumentService;
import com.knowledge.kb.service.KbParseTaskService;
import com.knowledge.kb.service.KbPermissionService;
import com.knowledge.kb.service.KbUploadRecordService;
import com.knowledge.kb.service.KnowledgeBaseService;
import com.knowledge.kb.storage.StorageService;
import com.knowledge.kb.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * 文档服务实现
 * <p>核心流程：上传 → 校验 → 存储 → 入库 → 上传记录 → 解析任务 → 计数
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument>
        implements KbDocumentService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KbUploadRecordService kbUploadRecordService;
    private final KbParseTaskService kbParseTaskService;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final KbPermissionService kbPermissionService;
    private final KbDocAclService kbDocAclService;
    private final DocVisibleSetService docVisibleSetService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResultVo upload(Long kbId, MultipartFile file, Long userId) {
        // 0. 权限校验：需 editor 及以上角色
        kbPermissionService.checkEditor(kbId, userId);

        // 1. 校验知识库
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null) {
            throw new BizException("知识库不存在");
        }
        if (kb.getStatus() != null && kb.getStatus() == KbConstants.KB_STATUS_DISABLED) {
            throw new BizException("知识库已停用，无法上传");
        }

        String originalName = file == null ? null : file.getOriginalFilename();

        // 2. 存储文件（内部完成类型 / 大小校验）；失败则记录上传失败
        StoredFile storedFile;
        try {
            storedFile = storageService.store(file);
        } catch (BizException e) {
            kbUploadRecordService.recordFailure(kbId, originalName,
                    file == null ? 0L : file.getSize(), resolveType(originalName), e.getMessage(), userId);
            throw e;
        }

        // 3. 文档入库（待解析）
        KbDocument doc = new KbDocument();
        // 多租户兜底：与知识库所在租户保持一致（避免 MP 拦截器遗漏）
        Long kbTenantId = kb.getTenantId() != null ? kb.getTenantId() : com.knowledge.common.context.TenantContext.requiredTenantId();
        doc.setTenantId(kbTenantId);
        doc.setKbId(kbId);
        doc.setOriginalName(storedFile.getOriginalName());
        doc.setStoredName(storedFile.getStoredName());
        doc.setFilePath(storedFile.getRelativePath());
        doc.setFileSize(storedFile.getSize());
        doc.setFileType(storedFile.getFileType());
        doc.setFileSuffix(storedFile.getSuffix());
        doc.setMd5(storedFile.getMd5());
        doc.setStatus(KbConstants.DOC_STATUS_PENDING);
        doc.setChunkCount(0);
        // 知识治理初始态：版本1 + 待审核
        doc.setVersion(1);
        doc.setReviewStatus(com.knowledge.kb.governance.enums.ReviewStatus.PENDING.name());
        doc.setCreatorId(userId);
        // 【文档级权限 · 兜底】默认 PUBLIC + 继承 KB 权限；后续可通过 ACL 接口精细化收回
        doc.setVisibility(com.knowledge.kb.permission.DocVisibility.PUBLIC.getCode());
        doc.setInheritKbPermission(1);
        save(doc);

        // 3.1 【默认 ACL 兜底】创建者拥有 5 个权限 ALLOW（否则 creatorId 检查已覆盖 VIEW/EDIT/DELETE/DOWNLOAD/SHARE，
        //       这里显式写 ACL 为后续"转交文档/移除创建者"场景做兼容，避免转移后原创建者残留权限、新文档负责人丢失权限
        for (DocPermission perm : DocPermission.values()) {
            KbDocAcl acl = new KbDocAcl();
            acl.setTenantId(kbTenantId);
            acl.setDocId(doc.getId());
            acl.setSubjectType(AclSubjectType.USER.getCode());
            acl.setSubjectId(userId);
            acl.setPermission(perm.name());
            acl.setEffect("A");
            kbDocAclService.save(acl);
        }

        // 4. 记录上传成功
        kbUploadRecordService.recordSuccess(doc.getId(), kbId, storedFile, userId);

        // 5. 创建解析任务（待处理）并异步触发 RAG 入库流程
        //    注意：process 是 @Async，若在事务提交前调用，新线程查询 task 会因事务未提交而查不到，
        //    故注册 afterCommit 回调，确保事务提交后再派发解析任务
        KbParseTask task = kbParseTaskService.createTask(doc.getId(), kbId, userId);
        final Long taskId = task.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kbParseTaskService.process(taskId);
            }
        });

        // 6. 知识库文档数 +1
        knowledgeBaseService.incrDocCount(kbId);

        log.info("[文档上传] kb={} doc={} task={} size={}B", kbId, doc.getId(), task.getId(), storedFile.getSize());

        // 6.1 可见集合缓存失效：新文档入 KB → 所有用户可见集合失效（PUBLIC 或 inherit 都会影响全局可见集合）
        docVisibleSetService.evictForKb(kbId);

        UploadResultVo vo = new UploadResultVo();
        vo.setDocumentId(doc.getId());
        vo.setTaskId(task.getId());
        vo.setOriginalName(storedFile.getOriginalName());
        vo.setFileSize(storedFile.getSize());
        vo.setFileType(storedFile.getFileType());
        vo.setStatus(KbConstants.DOC_STATUS_PENDING);
        return vo;
    }

    @Override
    public IPage<KbDocumentVo> page(KbDocumentQuery query) {
        return baseMapper.selectDocumentPage(query.toPage(), query);
    }

    @Override
    public KbDocumentVo getDetail(Long id) {
        KbDocument doc = getById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        KbDocumentVo vo = new KbDocumentVo();
        BeanUtil.copyProperties(doc, vo);
        KnowledgeBase kb = knowledgeBaseService.getById(doc.getKbId());
        if (kb != null) {
            vo.setKbName(kb.getName());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        KbDocument doc = getById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        // 权限校验：删除文档需 editor 及以上角色
        kbPermissionService.checkEditor(doc.getKbId(), SecurityUtils.currentUserId());
        removeById(id);
        knowledgeBaseService.decrDocCount(doc.getKbId());
        // 级联失效该文档的解析任务：文档删除后任务 JOIN 不到原文档，任务列表会显示"空文档名"记录
        kbParseTaskService.lambdaUpdate()
                .eq(KbParseTask::getDocumentId, id)
                .ne(KbParseTask::getStatus, KbConstants.TASK_STATUS_FAILED)
                .set(KbParseTask::getStatus, KbConstants.TASK_STATUS_FAILED)
                .set(KbParseTask::getErrorMsg, "文档已删除")
                .set(KbParseTask::getEndTime, LocalDateTime.now())
                .update();
        // 发布删除事件，由 ai 模块清理 Milvus 中该文档的向量
        eventPublisher.publishEvent(new DocumentDeletedEvent(id, doc.getKbId()));
        log.info("[文档删除] doc={} kb={}", id, doc.getKbId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long triggerParse(Long documentId, Long userId) {
        KbDocument doc = getById(documentId);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        // 复用仍处于"待处理/处理中"的任务，避免重复触发
        KbParseTask active = kbParseTaskService.lambdaQuery()
                .eq(KbParseTask::getDocumentId, documentId)
                .in(KbParseTask::getStatus,
                        KbConstants.TASK_STATUS_PENDING,
                        KbConstants.TASK_STATUS_PROCESSING)
                .last("LIMIT 1")
                .one();
        if (active != null) {
            // PENDING 说明上次投递可能丢失/未被消费（如 MQ 宕机、消费异常），重新派发保证任务不卡死；
            // 消费端 markProcessingIfPending 条件抢占保证幂等（重复消息不会重复解析）。
            // PROCESSING 表示正在处理中，不重复派发。
            if (active.getStatus() == KbConstants.TASK_STATUS_PENDING) {
                final Long activeId = active.getId();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        kbParseTaskService.process(activeId);
                    }
                });
                log.info("[文档解析] doc={} 复用待处理任务 task={}，重新派发", documentId, activeId);
            }
            return active.getId();
        }
        KbParseTask task = kbParseTaskService.createTask(documentId, doc.getKbId(), userId);
        // 重新触发解析：文档状态重置为待解析，与新建任务状态保持一致
        // （文档可能处于"已解析"或"解析失败"，重置避免"任务待处理但文档状态滞后"的不一致）
        doc.setStatus(KbConstants.DOC_STATUS_PENDING);
        updateById(doc);
        final Long reparseTaskId = task.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kbParseTaskService.process(reparseTaskId);
            }
        });
        return task.getId();
    }

    /** 从文件名推断类型（上传失败记录用） */
    private String resolveType(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
    }

    @Override
    public java.util.List<KbDocument> listRecentParsed(int hours) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusHours(hours);
        return lambdaQuery()
                .eq(KbDocument::getStatus, KbConstants.DOC_STATUS_PARSED)
                .ge(KbDocument::getUpdateTime, cutoff)
                .orderByDesc(KbDocument::getId)
                .list();
    }
}
