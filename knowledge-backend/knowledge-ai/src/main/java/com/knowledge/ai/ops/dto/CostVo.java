package com.knowledge.ai.ops.dto;

import java.math.BigDecimal;

/**
 * 费用分析相关视图对象（运维看板费用模块）。
 * <p>以嵌套 record 形式承载：日趋势 / 模型占比 / 预算校验 / 月度预测。
 *
 * @author: lxcechoo@gmail.com
 */
public final class CostVo {

    private CostVo() {
    }

    /** 单日费用点（趋势折线图） */
    public record DailyPoint(String day, long calls, long tokens, BigDecimal cost) {
    }

    /** 单模型费用点（占比饼图） */
    public record ModelPoint(String model, long calls, long tokens, BigDecimal cost) {
    }

    /**
     * 预算校验结果。
     *
     * @param monthCost   当月累计费用（元）
     * @param budget      月度预算阈值（元）
     * @param exceeded    是否超预算
     * @param usedPercent 已用百分比 [0,∞)
     */
    public record BudgetStatus(BigDecimal monthCost, BigDecimal budget, boolean exceeded, double usedPercent) {
    }

    /**
     * 月度费用预测。
     *
     * @param monthToDate    当月已发生费用（元）
     * @param dailyAvg7d     最近 7 天日均费用（元）
     * @param remainingDays  当月剩余天数（含今日）
     * @param forecast       预测月底总费用 = monthToDate + dailyAvg7d × remainingDays
     * @param note           预测说明（线性预测假设）
     */
    public record Forecast(BigDecimal monthToDate, double dailyAvg7d, int remainingDays,
                           BigDecimal forecast, String note) {
    }
}
