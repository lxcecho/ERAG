package com.knowledge.ai.calllog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI 调用统计结果。
 * <p>包含概览指标 + 按模型/用户/日期的明细排行，供运营统计页展示。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class AiCallStatsVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总调用次数 */
    private long totalCalls;
    /** 成功次数 */
    private long successCount;
    /** 失败次数 */
    private long failedCount;
    /** 总 token 消耗 */
    private long totalTokens;
    /** 输入 token */
    private long promptTokens;
    /** 输出 token */
    private long completionTokens;
    /** 总费用（元） */
    private BigDecimal totalCost;
    /** 平均耗时（ms） */
    private long avgDurationMs;

    /** 按模型统计（费用倒序） */
    private List<ItemStat> byModel;
    /** 按用户统计（费用倒序，Top 10） */
    private List<ItemStat> byUser;
    /** 按日期统计（最近 30 天，日期倒序） */
    private List<DailyStat> byDay;

    /** 维度统计项（模型/用户通用） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemStat implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 维度名：模型名 或 用户名 */
        private String key;
        /** 用户ID（byUser 用） */
        private Long userId;
        private long calls;
        private long tokens;
        private BigDecimal cost;
    }

    /** 日期统计项 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStat implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 日期 yyyy-MM-dd */
        private String day;
        private long calls;
        private long tokens;
        private BigDecimal cost;
    }
}
