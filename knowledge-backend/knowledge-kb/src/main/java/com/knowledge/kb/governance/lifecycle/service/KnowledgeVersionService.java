package com.knowledge.kb.governance.lifecycle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.kb.governance.dto.GovernanceQuery;
import com.knowledge.kb.governance.dto.VersionVo;
import com.knowledge.kb.governance.lifecycle.dto.VersionRollbackRequest;

/**
 * 知识版本门面服务：复用 {@code document_version} 表，叠加版本回滚能力。
 * <p>版本列表 / 记录版本透传既有 {@code KnowledgeGovernanceService}；
 * {@link #rollback} 为新增能力：恢复历史快照 + 版本号自增 + 必要时触发生命周期重审。
 *
 * @author: lxcechoo@gmail.com
 */
public interface KnowledgeVersionService {

    /** 版本历史分页（透传） */
    IPage<VersionVo> versionPage(GovernanceQuery query);

    /** 记录当前版本快照（透传，changeLog / userId） */
    void recordVersion(Long docId, String changeLog, Long userId);

    /**
     * 回滚至指定历史版本。
     * <ol>
     *   <li>校验目标版本存在；</li>
     *   <li>新版本号 = 当前版本 + 1；</li>
     *   <li>目标快照 storedName/filePath/fileSize/md5 写回主表，version 置为新号；</li>
     *   <li>插入新 DocumentVersion（避开 uk_doc_version 重复）；</li>
     *   <li>若 lifecycleStatus=PUBLISHED 且 policy.requireReview=true，触发 UPDATE 重审；</li>
     *   <li>记 VERSION_ROLLBACK 审计。</li>
     * </ol>
     */
    void rollback(VersionRollbackRequest request, Long userId);
}
