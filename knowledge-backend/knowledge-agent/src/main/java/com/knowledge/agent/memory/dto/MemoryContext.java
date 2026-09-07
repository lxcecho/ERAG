package com.knowledge.agent.memory.dto;

import com.knowledge.ai.chat.dto.ChatMessageVo;
import lombok.Data;

import java.util.List;

/**
 * 装配后的记忆上下文（MemoryService.loadContext 返回），注入 PlannerAgent 提示词作为用户历史背景。
 * <p>四类记忆汇总：近期会话轮次 + 会话摘要 + 用户长期事实 + 向量语义召回。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class MemoryContext {

    /** 当前会话近期消息（按时间正序） */
    private List<ChatMessageVo> recentMessages;

    /** 当前会话摘要（最新一条 SUMMARY） */
    private String summary;

    /** 用户长期事实/偏好（LONG_TERM 列表，按时间倒序） */
    private List<String> longTermFacts;

    /** 向量语义召回（与当前 query 相关的历史记忆） */
    private List<MemoryRecall> vectorRecalls;

    /** 是否完全为空（无任何记忆可用） */
    public boolean isEmpty() {
        return (recentMessages == null || recentMessages.isEmpty())
                && (summary == null || summary.isBlank())
                && (longTermFacts == null || longTermFacts.isEmpty())
                && (vectorRecalls == null || vectorRecalls.isEmpty());
    }

    /**
     * 拼接为 Planner 可用的背景文本。
     * <p>格式化各记忆段为 Markdown 列表，空段省略；全空返回空串（Planner 据此跳过注入）。
     *
     * @return 背景文本，无记忆时返回 ""
     */
    public String toPromptText() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            sb.append("【会话摘要】").append(summary).append("\n");
        }
        if (longTermFacts != null && !longTermFacts.isEmpty()) {
            sb.append("【用户长期事实】\n");
            for (int i = 0; i < longTermFacts.size(); i++) {
                sb.append(i + 1).append(". ").append(longTermFacts.get(i)).append("\n");
            }
        }
        if (recentMessages != null && !recentMessages.isEmpty()) {
            sb.append("【近期对话】\n");
            for (ChatMessageVo m : recentMessages) {
                sb.append(m.getRole()).append(": ").append(truncate(m.getContent(), 200)).append("\n");
            }
        }
        if (vectorRecalls != null && !vectorRecalls.isEmpty()) {
            sb.append("【相关历史记忆】\n");
            for (MemoryRecall r : vectorRecalls) {
                sb.append("- ").append(truncate(r.getContent(), 200)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }
}
