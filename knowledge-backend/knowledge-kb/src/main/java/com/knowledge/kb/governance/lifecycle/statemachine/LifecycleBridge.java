package com.knowledge.kb.governance.lifecycle.statemachine;

import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.governance.enums.ReviewStatus;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleStatus;

import java.time.LocalDateTime;

/**
 * 生命周期 ↔ 审核状态桥接器。
 * <p>每次生命周期迁移后由 {@code KnowledgeLifecycleServiceImpl} 调用，按目标 lifecycle_status 同步
 * review_status / reviewerId / reviewedAt / archivedAt，保证新旧两套状态字段一致：
 * <ul>
 *   <li>REVIEW（SUBMIT/UPDATE）→ review_status=PENDING，清空审核人与审核时间；</li>
 *   <li>PUBLISHED（APPROVE/PUBLISH/RESTORE）→ review_status=APPROVED，审核人=操作人，审核时间=now，清空归档时间；</li>
 *   <li>DRAFT（REJECT）→ review_status=REJECTED，审核人=操作人，审核时间=now；</li>
 *   <li>ARCHIVED → review_status 保持 APPROVED，归档时间=now。</li>
 * </ul>
 * <p>非破坏：旧 {@code KnowledgeGovernanceServiceImpl.review()} 不动；本桥接仅在新生命周期端点路径生效。
 *
 * @author: lxcechoo@gmail.com
 */
public final class LifecycleBridge {

    private LifecycleBridge() {
    }

    /**
     * 按目标生命周期状态同步文档审核相关字段（原地修改 doc）。
     *
     * @param doc        文档（将被修改）
     * @param target     迁移后的生命周期状态
     * @param operatorId 操作人ID（审核/发布/驳回执行者）
     */
    public static void syncReviewFields(KbDocument doc, LifecycleStatus target, Long operatorId) {
        if (doc == null || target == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        switch (target) {
            case REVIEW -> {
                doc.setReviewStatus(ReviewStatus.PENDING.name());
                doc.setReviewerId(null);
                doc.setReviewedAt(null);
                doc.setArchivedAt(null);
            }
            case PUBLISHED -> {
                doc.setReviewStatus(ReviewStatus.APPROVED.name());
                doc.setReviewerId(operatorId);
                doc.setReviewedAt(now);
                doc.setArchivedAt(null);
            }
            case DRAFT -> {
                // 仅 REJECT 到达此分支：驳回
                doc.setReviewStatus(ReviewStatus.REJECTED.name());
                doc.setReviewerId(operatorId);
                doc.setReviewedAt(now);
                doc.setArchivedAt(null);
            }
            case ARCHIVED -> {
                // 保持 review_status=APPROVED，仅记归档时间
                doc.setArchivedAt(now);
            }
            default -> {
                // 无操作
            }
        }
    }
}
