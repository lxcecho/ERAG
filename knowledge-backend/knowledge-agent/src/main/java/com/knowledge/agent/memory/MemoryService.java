package com.knowledge.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.agent.memory.dto.MemoryContext;
import com.knowledge.agent.memory.dto.MemoryEntryVo;
import com.knowledge.agent.memory.dto.MemoryRecall;
import com.knowledge.agent.memory.entity.AgentMemory;
import com.knowledge.agent.memory.mapper.AgentMemoryMapper;
import com.knowledge.common.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 记忆中心门面：聚合四类记忆，对 Agent 层提供「加载上下文 + 记录交互 + CRUD」统一入口。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>{@link #loadContext}：装配 {@link MemoryContext}（4 类记忆汇总），供 PlannerAgent 注入提示词；</li>
 *   <li>{@link #recordInteraction}：每轮写会话消息 + 达阈值时异步触发摘要/事实抽取（手动线程池 + TenantContext 传播）；</li>
 *   <li>CRUD：手动触发摘要/抽取、列表查询、删除、语义召回演示。</li>
 * </ul>
 * <p>
 * 异步模式：{@code memoryTaskPool} + 显式 TenantContext.setTenantId/clear（对齐 WorkflowExecutor.runAsync），
 * 不依赖 @EnableAsync，保证 TransmittableThreadLocal 在自定义线程池中正确传播。
 * <p>
 * best-effort：loadContext 各子记忆独立 try-catch，单点失败不影响其他记忆加载，绝不阻断主流程。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class MemoryService {

    private static final String ROLE_USER = "user";

    private final ConversationMemory conversationMemory;
    private final SummaryMemory summaryMemory;
    private final LongTermMemory longTermMemory;
    private final VectorMemory vectorMemory;
    private final AgentMemoryMapper memoryMapper;
    private final MemoryProperties props;
    private final ExecutorService memoryTaskPool;

    public MemoryService(ConversationMemory conversationMemory,
                         SummaryMemory summaryMemory,
                         LongTermMemory longTermMemory,
                         VectorMemory vectorMemory,
                         AgentMemoryMapper memoryMapper,
                         MemoryProperties props,
                         @Qualifier("memoryTaskPool") ExecutorService memoryTaskPool) {
        this.conversationMemory = conversationMemory;
        this.summaryMemory = summaryMemory;
        this.longTermMemory = longTermMemory;
        this.vectorMemory = vectorMemory;
        this.memoryMapper = memoryMapper;
        this.props = props;
        this.memoryTaskPool = memoryTaskPool;
    }

    /**
     * 装配记忆上下文（4 类记忆汇总），供 PlannerAgent 注入提示词。
     * <p>各子记忆独立 try-catch，单点失败降级为空，不阻断规划。
     *
     * @param sessionId 会话ID（可空，非聊天任务）
     * @param userId    用户ID
     * @param tenantId  租户ID
     * @param query     当前用户目标/问题（向量召回用）
     * @return 装配结果（可能部分为空）
     */
    public MemoryContext loadContext(Long sessionId, Long userId, Long tenantId, String query) {
        MemoryContext ctx = new MemoryContext();
        if (sessionId != null) {
            try {
                ctx.setRecentMessages(conversationMemory.loadRecent(sessionId, props.getConversationWindow()));
            } catch (Exception e) {
                log.warn("[Memory:Facade] 加载会话记忆失败 session={} err={}", sessionId, e.getMessage());
            }
            try {
                ctx.setSummary(summaryMemory.loadSummary(sessionId));
            } catch (Exception e) {
                log.warn("[Memory:Facade] 加载摘要失败 session={} err={}", sessionId, e.getMessage());
            }
        }
        if (userId != null) {
            try {
                ctx.setLongTermFacts(longTermMemory.loadFacts(userId));
            } catch (Exception e) {
                log.warn("[Memory:Facade] 加载长期事实失败 user={} err={}", userId, e.getMessage());
            }
        }
        if (query != null && !query.isBlank() && userId != null) {
            try {
                ctx.setVectorRecalls(vectorMemory.recall(query, userId, props.getRecallTopK()));
            } catch (Exception e) {
                log.warn("[Memory:Facade] 向量召回失败 user={} err={}", userId, e.getMessage());
            }
        }
        return ctx;
    }

    /**
     * 记录一轮交互：写会话消息 + 达阈值异步触发摘要/事实抽取。
     * <p>同步部分（写 DB）失败将抛出由上游兜底；异步抽取 best-effort 不影响主流程。
     *
     * @param sessionId    会话ID
     * @param userId       用户ID
     * @param tenantId     租户ID
     * @param userMsg      用户消息
     * @param assistantMsg 助手消息
     */
    public void recordInteraction(Long sessionId, Long userId, Long tenantId,
                                  String userMsg, String assistantMsg) {
        // 1. 同步写会话消息（含缓存失效）
        conversationMemory.append(sessionId, userMsg, assistantMsg);

        // 2. 计算轮次，达阈值异步抽取（无状态去重：仅在阈值的整数倍轮次触发，避免每轮重复抽取）
        try {
            List<ChatMessageVo> recent = conversationMemory.loadRecent(sessionId, Integer.MAX_VALUE);
            int turnCount = countTurns(recent);
            boolean needSummary = shouldTrigger(turnCount, props.getSummaryThreshold());
            boolean needExtract = shouldTrigger(turnCount, props.getLongTermThreshold());
            if (!needSummary && !needExtract) {
                return;
            }
            final int turns = turnCount;
            memoryTaskPool.submit(() -> runAsync(tenantId, () -> {
                if (needSummary) {
                    summaryMemory.summarize(sessionId, userId, tenantId);
                }
                if (needExtract) {
                    longTermMemory.extractFacts(sessionId, userId, tenantId);
                }
                log.info("[Memory:Facade] 异步抽取完成 session={} turns={}", sessionId, turns);
            }));
        } catch (Exception e) {
            log.warn("[Memory:Facade] 异步抽取触发失败 session={} err={}", sessionId, e.getMessage());
        }
    }

    /** 手动触发会话摘要生成 */
    public String summarize(Long sessionId, Long userId, Long tenantId) {
        return summaryMemory.summarize(sessionId, userId, tenantId);
    }

    /** 手动触发长期事实抽取 */
    public List<String> extractFacts(Long sessionId, Long userId, Long tenantId) {
        return longTermMemory.extractFacts(sessionId, userId, tenantId);
    }

    /** 查询用户记忆列表（可选类型过滤） */
    public List<MemoryEntryVo> listMemories(Long userId, String type) {
        LambdaQueryWrapper<AgentMemory> qw = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getUserId, userId)
                .orderByDesc(AgentMemory::getId);
        if (type != null && !type.isBlank()) {
            qw.eq(AgentMemory::getMemoryType, type.toUpperCase());
        }
        return memoryMapper.selectList(qw).stream().map(this::toVo).toList();
    }

    /** 删除记忆条目（DB 逻辑删除 + 向量清理 best-effort） */
    public void deleteMemory(Long id) {
        AgentMemory memory = memoryMapper.selectById(id);
        if (memory == null) {
            return;
        }
        memoryMapper.deleteById(id);
        vectorMemory.removeByMemoryId(id);
        log.info("[Memory:Facade] 删除记忆 id={} type={}", id, memory.getMemoryType());
    }

    /** 向量语义召回演示 */
    public List<MemoryRecall> recall(String query, Long userId, int topK) {
        return vectorMemory.recall(query, userId, topK);
    }

    // ==================== 辅助 ====================

    /** 异步执行模板：显式传播租户上下文 + 异常兜底 + 清理（对齐 WorkflowExecutor.runAsync） */
    private void runAsync(Long tenantId, Runnable action) {
        TenantContext.setTenantId(tenantId);
        try {
            action.run();
        } catch (Exception e) {
            log.error("[Memory:Facade] 异步任务执行失败 tenant={} err={}", tenantId, e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /** 统计会话轮次（1 轮 = 1 条 user 消息） */
    private static int countTurns(List<ChatMessageVo> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return (int) messages.stream()
                .filter(m -> ROLE_USER.equalsIgnoreCase(m.getRole()))
                .count();
    }

    /**
     * 触发判定：轮次达到阈值且为阈值的整数倍（无状态去重，避免每轮重复触发）。
     * <p>例：阈值=10，则在第 10/20/30... 轮各触发一次；中间轮次不触发。
     */
    private static boolean shouldTrigger(int turnCount, int threshold) {
        if (threshold <= 0 || turnCount < threshold) {
            return false;
        }
        return turnCount % threshold == 0;
    }

    private MemoryEntryVo toVo(AgentMemory m) {
        MemoryEntryVo vo = new MemoryEntryVo();
        vo.setId(m.getId());
        vo.setUserId(m.getUserId());
        vo.setSessionId(m.getSessionId());
        vo.setMemoryType(m.getMemoryType());
        vo.setContent(m.getContent());
        vo.setSourceSessionId(m.getSourceSessionId());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }
}
