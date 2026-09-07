package com.knowledge.ai.ops.cost;

import com.knowledge.ai.calllog.mapper.AiCallLogMapper;
import com.knowledge.ai.ops.config.OpsProperties;
import com.knowledge.ai.ops.dto.CostVo;
import com.knowledge.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 费用分析器：组合 {@link AiCallLogMapper} 与 {@link com.knowledge.ai.calllog.service.CostCalculator}
 * 能力，<b>不继承 {@code AiCallLogService}</b>（解耦，避免统计逻辑与日志 CRUD 耦合）。
 * <p>所有费用取自 {@code ai_call_log.cost} 列（单调用入库时已由 CostCalculator 计算并持久化），
 * 此处仅做聚合与预算/预测分析，不重算单价。
 * <p>覆盖运维看板 4 个费用端点：日趋势 / 模型占比 / 预算校验 / 月度预测。
 *
 * @author: lxcechoo@gmail.com
 */
@Component
@RequiredArgsConstructor
public class CostAnalyzer {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiCallLogMapper aiCallLogMapper;
    private final OpsProperties opsProperties;

    /** 按日费用趋势（复用 statsByDay，最近 30 天） */
    public List<CostVo.DailyPoint> dailyTrend(Long tenantId, String start, String end) {
        Long tid = tenantId != null ? tenantId : currentTenantId();
        String[] range = defaultRange(start, end);
        List<CostVo.DailyPoint> points = new ArrayList<>();
        for (Map<String, Object> row : aiCallLogMapper.statsByDay(tid, range[0], range[1])) {
            Object dayObj = row.get("day");
            points.add(new CostVo.DailyPoint(
                    dayObj == null ? "" : dayObj.toString(),
                    toLong(row.get("calls")),
                    toLong(row.get("tokens")),
                    toBigDecimal(row.get("cost"))));
        }
        return points;
    }

    /** 按模型费用占比（复用 statsByModel，费用倒序） */
    public List<CostVo.ModelPoint> costByModel(Long tenantId, String start, String end) {
        Long tid = tenantId != null ? tenantId : currentTenantId();
        String[] range = defaultRange(start, end);
        List<CostVo.ModelPoint> points = new ArrayList<>();
        for (Map<String, Object> row : aiCallLogMapper.statsByModel(tid, range[0], range[1])) {
            points.add(new CostVo.ModelPoint(
                    (String) row.get("dim"),
                    toLong(row.get("calls")),
                    toLong(row.get("tokens")),
                    toBigDecimal(row.get("cost"))));
        }
        return points;
    }

    /**
     * 预算校验：当月累计费用 vs 月度预算阈值。
     * <p>budget 为 null 时取 {@code ops.alert.monthly-budget} 配置。
     */
    public CostVo.BudgetStatus checkBudget(Long tenantId, BigDecimal budget) {
        Long tid = tenantId != null ? tenantId : currentTenantId();
        BigDecimal threshold = budget != null ? budget
                : BigDecimal.valueOf(opsProperties.getAlert().getMonthlyBudget());
        // 当月累计：start=月初, end=现在
        LocalDateTime now = LocalDateTime.now();
        String start = now.toLocalDate().withDayOfMonth(1).atStartOfDay().format(FMT);
        String end = now.format(FMT);
        Map<String, Object> ov = aiCallLogMapper.statsOverview(tid, start, end);
        BigDecimal monthCost = toBigDecimal(ov.get("cost"));
        double usedPercent = threshold.signum() == 0 ? 0
                : monthCost.divide(threshold, 4, RoundingMode.HALF_UP).doubleValue() * 100;
        boolean exceeded = monthCost.compareTo(threshold) >= 0;
        return new CostVo.BudgetStatus(monthCost, threshold, exceeded, usedPercent);
    }

    /**
     * 月度费用预测：月底总费用 ≈ 当月已发生 + 最近 7 天日均 × 剩余天数。
     * <p>线性预测假设（流量平稳），note 标注假设供前端展示。
     */
    public CostVo.Forecast forecast(Long tenantId) {
        Long tid = tenantId != null ? tenantId : currentTenantId();
        LocalDate today = LocalDate.now();
        // 当月已发生费用
        String monthStart = today.withDayOfMonth(1).atStartOfDay().format(FMT);
        String now = LocalDateTime.now().format(FMT);
        Map<String, Object> monthOv = aiCallLogMapper.statsOverview(tid, monthStart, now);
        BigDecimal monthToDate = toBigDecimal(monthOv.get("cost"));

        // 最近 7 天日均
        String d7Start = today.minusDays(6).atStartOfDay().format(FMT);
        Map<String, Object> d7Ov = aiCallLogMapper.statsOverview(tid, d7Start, now);
        BigDecimal d7Cost = toBigDecimal(d7Ov.get("cost"));
        double dailyAvg7d = d7Cost.divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP).doubleValue();

        // 当月剩余天数（含今日）：月末号 - 今日号 + 1... 实取 (月末 - today) 天数
        YearMonth ym = YearMonth.from(today);
        int dayOfMonth = today.getDayOfMonth();
        int lastDay = ym.lengthOfMonth();
        int remainingDays = Math.max(0, lastDay - dayOfMonth);

        BigDecimal forecastAdd = BigDecimal.valueOf(dailyAvg7d * remainingDays)
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal forecast = monthToDate.add(forecastAdd);
        return new CostVo.Forecast(monthToDate, dailyAvg7d, remainingDays, forecast,
                "线性预测：假设流量与最近7天一致");
    }

    /* ==================== 工具 ==================== */

    /** 默认最近 7 天范围（start/end 为空时填充） */
    private String[] defaultRange(String start, String end) {
        if ((start == null || start.isBlank()) && (end == null || end.isBlank())) {
            LocalDateTime now = LocalDateTime.now();
            return new String[]{now.minusDays(7).format(FMT), now.format(FMT)};
        }
        return new String[]{start, end};
    }

    private static Long currentTenantId() {
        Long id = TenantContext.getTenantId();
        return id != null ? id : TenantContext.PLATFORM_TENANT_ID;
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
