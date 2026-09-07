package com.knowledge.ai.dto;

/**
 * 工作台统计视图对象（GET /dashboard/stats）。
 * <p>四项核心指标：知识库数 / 文档数 / 切片数 / 今日对话数。
 *
 * @author: lxcechoo@gmail.com
 */
public record DashboardStatsVo(
        /** 知识库数量 */
        long kbCount,
        /** 文档数量 */
        long docCount,
        /** 切片数量 */
        long chunkCount,
        /** 今日对话数（用户提问条数） */
        long todayChatCount) {
}
