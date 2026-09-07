package com.knowledge.kb.governance.lifecycle.statemachine;

import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleAction;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleStatus;
import org.springframework.stereotype.Component;

/**
 * 生命周期状态机：纯函数裁定居留迁移合法性。
 * <p>迁移矩阵（policy.requireReview 影响出 DRAFT 的两条路径与 UPDATE）：
 * <pre>
 *   DRAFT   --SUBMIT(requireReview=true)-->  REVIEW
 *   DRAFT   --PUBLISH(requireReview=false)-> PUBLISHED
 *   REVIEW  --APPROVE-->                     PUBLISHED
 *   REVIEW  --REJECT-->                      DRAFT
 *   PUBLISHED --ARCHIVE-->                   ARCHIVED
 *   ARCHIVED --RESTORE-->                    PUBLISHED
 *   PUBLISHED --UPDATE(requireReview=true)--> REVIEW
 *   PUBLISHED --UPDATE(requireReview=false)--> PUBLISHED（仅记版本，不重审）
 * </pre>
 * 其余组合返回 null（非法流转）。
 *
 * @author: lxcechoo@gmail.com
 */
@Component
public class LifecycleStateMachine {

    /**
     * 计算迁移后的状态。
     *
     * @param current 当前生命周期状态
     * @param action  生命周期动作
     * @param policy  知识库策略（影响 requireReview 分支）
     * @return 迁移后的状态；非法组合返回 null
     */
    public LifecycleStatus nextStatus(LifecycleStatus current, LifecycleAction action, KnowledgePolicy policy) {
        if (current == null || action == null) {
            return null;
        }
        boolean requireReview = policy == null || policy.getRequireReview() == null || policy.getRequireReview();

        switch (action) {
            case SUBMIT:
                // requireReview=true 时 DRAFT→REVIEW
                return (current == LifecycleStatus.DRAFT && requireReview) ? LifecycleStatus.REVIEW : null;
            case PUBLISH:
                // requireReview=false 时 DRAFT→PUBLISHED（跳过审核）
                return (current == LifecycleStatus.DRAFT && !requireReview) ? LifecycleStatus.PUBLISHED : null;
            case APPROVE:
                return current == LifecycleStatus.REVIEW ? LifecycleStatus.PUBLISHED : null;
            case REJECT:
                return current == LifecycleStatus.REVIEW ? LifecycleStatus.DRAFT : null;
            case ARCHIVE:
                return current == LifecycleStatus.PUBLISHED ? LifecycleStatus.ARCHIVED : null;
            case RESTORE:
                return current == LifecycleStatus.ARCHIVED ? LifecycleStatus.PUBLISHED : null;
            case UPDATE:
                // 已发布文档更新：requireReview=true 重审→REVIEW；false 保持 PUBLISHED（仅记版本）
                if (current != LifecycleStatus.PUBLISHED) {
                    return null;
                }
                return requireReview ? LifecycleStatus.REVIEW : LifecycleStatus.PUBLISHED;
            default:
                return null;
        }
    }
}
