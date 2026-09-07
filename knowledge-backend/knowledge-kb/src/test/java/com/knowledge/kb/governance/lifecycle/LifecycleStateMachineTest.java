/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.kb.governance.lifecycle;

import com.knowledge.kb.governance.lifecycle.entity.KnowledgePolicy;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleAction;
import com.knowledge.kb.governance.lifecycle.enums.LifecycleStatus;
import com.knowledge.kb.governance.lifecycle.statemachine.LifecycleStateMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生命周期状态机单元测试。
 * <p>验证：迁移矩阵全分支 + 非法组合返回 null + isRetrievable/isTerminal 边界。
 */
class LifecycleStateMachineTest {

    private final LifecycleStateMachine machine = new LifecycleStateMachine();

    /** requireReview=true 的策略 */
    private KnowledgePolicy policy(boolean requireReview) {
        KnowledgePolicy p = new KnowledgePolicy();
        p.setRequireReview(requireReview);
        return p;
    }

    /* ==================== 合法迁移 ==================== */

    @Test
    void 提交审核_草稿且强制审核_进入审核中() {
        LifecycleStatus next = machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.SUBMIT, policy(true));
        assertEquals(LifecycleStatus.REVIEW, next);
    }

    @Test
    void 直接发布_草稿且免审核_进入已发布() {
        LifecycleStatus next = machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.PUBLISH, policy(false));
        assertEquals(LifecycleStatus.PUBLISHED, next);
    }

    @Test
    void 审核通过_审核中_进入已发布() {
        LifecycleStatus next = machine.nextStatus(LifecycleStatus.REVIEW, LifecycleAction.APPROVE, policy(true));
        assertEquals(LifecycleStatus.PUBLISHED, next);
    }

    @Test
    void 审核驳回_审核中_回到草稿() {
        LifecycleStatus next = machine.nextStatus(LifecycleStatus.REVIEW, LifecycleAction.REJECT, policy(true));
        assertEquals(LifecycleStatus.DRAFT, next);
    }

    @Test
    void 归档_已发布_进入已归档() {
        LifecycleStatus next = machine.nextStatus(LifecycleStatus.PUBLISHED, LifecycleAction.ARCHIVE, policy(true));
        assertEquals(LifecycleStatus.ARCHIVED, next);
    }

    @Test
    void 恢复_已归档_回到已发布() {
        LifecycleStatus next = machine.nextStatus(LifecycleStatus.ARCHIVED, LifecycleAction.RESTORE, policy(true));
        assertEquals(LifecycleStatus.PUBLISHED, next);
    }

    @Test
    void 更新重审_已发布且强制审核_进入审核中() {
        LifecycleStatus next = machine.nextStatus(LifecycleStatus.PUBLISHED, LifecycleAction.UPDATE, policy(true));
        assertEquals(LifecycleStatus.REVIEW, next);
    }

    @Test
    void 更新免重审_已发布且免审核_保持已发布() {
        LifecycleStatus next = machine.nextStatus(LifecycleStatus.PUBLISHED, LifecycleAction.UPDATE, policy(false));
        assertEquals(LifecycleStatus.PUBLISHED, next);
    }

    /* ==================== 非法迁移返回 null ==================== */

    @Test
    void 提交审核_草稿但免审核_非法返回null() {
        assertNull(machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.SUBMIT, policy(false)));
    }

    @Test
    void 直接发布_草稿但强制审核_非法返回null() {
        assertNull(machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.PUBLISH, policy(true)));
    }

    @Test
    void 审核通过_非审核中_非法返回null() {
        assertNull(machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.APPROVE, policy(true)));
        assertNull(machine.nextStatus(LifecycleStatus.PUBLISHED, LifecycleAction.APPROVE, policy(true)));
        assertNull(machine.nextStatus(LifecycleStatus.ARCHIVED, LifecycleAction.APPROVE, policy(true)));
    }

    @Test
    void 归档_非已发布_非法返回null() {
        assertNull(machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.ARCHIVE, policy(true)));
        assertNull(machine.nextStatus(LifecycleStatus.REVIEW, LifecycleAction.ARCHIVE, policy(true)));
        assertNull(machine.nextStatus(LifecycleStatus.ARCHIVED, LifecycleAction.ARCHIVE, policy(true)));
    }

    @Test
    void 恢复_非已归档_非法返回null() {
        assertNull(machine.nextStatus(LifecycleStatus.PUBLISHED, LifecycleAction.RESTORE, policy(true)));
        assertNull(machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.RESTORE, policy(true)));
    }

    @Test
    void 更新_非已发布_非法返回null() {
        assertNull(machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.UPDATE, policy(true)));
        assertNull(machine.nextStatus(LifecycleStatus.REVIEW, LifecycleAction.UPDATE, policy(true)));
        assertNull(machine.nextStatus(LifecycleStatus.ARCHIVED, LifecycleAction.UPDATE, policy(true)));
    }

    @Test
    void 空入参_非法返回null() {
        assertNull(machine.nextStatus(null, LifecycleAction.SUBMIT, policy(true)));
        assertNull(machine.nextStatus(LifecycleStatus.DRAFT, null, policy(true)));
    }

    @Test
    void 策略为null_默认强制审核() {
        // policy=null 等价 requireReview=true
        assertEquals(LifecycleStatus.REVIEW,
                machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.SUBMIT, null));
        assertNull(machine.nextStatus(LifecycleStatus.DRAFT, LifecycleAction.PUBLISH, null));
    }

    /* ==================== 枚举辅助方法 ==================== */

    @Test
    void 仅PUBLISHED可被检索() {
        assertTrue(LifecycleStatus.PUBLISHED.isRetrievable());
        assertFalse(LifecycleStatus.DRAFT.isRetrievable());
        assertFalse(LifecycleStatus.REVIEW.isRetrievable());
        assertFalse(LifecycleStatus.ARCHIVED.isRetrievable());
    }

    @Test
    void PUBLISHED与ARCHIVED为终态() {
        assertTrue(LifecycleStatus.PUBLISHED.isTerminal());
        assertTrue(LifecycleStatus.ARCHIVED.isTerminal());
        assertFalse(LifecycleStatus.DRAFT.isTerminal());
        assertFalse(LifecycleStatus.REVIEW.isTerminal());
    }
}
