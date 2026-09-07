package com.knowledge.ai.calllog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.ai.calllog.dto.AiCallLogQuery;
import com.knowledge.ai.calllog.dto.AiCallStatsQuery;
import com.knowledge.ai.calllog.dto.AiCallStatsVo;
import com.knowledge.ai.calllog.entity.AiCallLog;
import com.knowledge.ai.calllog.mapper.AiCallLogMapper;
import com.knowledge.ai.calllog.service.AiCallLogService;
import com.knowledge.common.config.RedisCacheService;
import com.knowledge.common.context.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 调用日志服务实现。
 * <p>统计时间范围默认最近 7 天；聚合结果由 Mapper 返回 Map 行，本类负责类型规整与 VO 装配。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class AiCallLogServiceImpl implements AiCallLogService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AiCallLogMapper mapper;
    /** Redis 缓存服务（cache.type=none 时为 null，走直查降级） */
    private final RedisCacheService cacheService;

    public AiCallLogServiceImpl(AiCallLogMapper mapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RedisCacheService cacheService) {
        this.mapper = mapper;
        this.cacheService = cacheService;
    }

    @Override
    public IPage<AiCallLog> page(AiCallLogQuery query) {
        return mapper.page(query.toPage(), currentTenantId(), query);
    }

    @Override
    public AiCallStatsVo stats(AiCallStatsQuery query) {
        final Long tenantId = currentTenantId();
        // 默认最近 7 天
        String rawStart = query.getStartTime();
        String rawEnd = query.getEndTime();
        final String start;
        final String end;
        if ((rawStart == null || rawStart.isBlank()) && (rawEnd == null || rawEnd.isBlank())) {
            end = LocalDateTime.now().format(FMT);
            start = LocalDateTime.now().minusDays(7).format(FMT);
        } else {
            start = rawStart;
            end = rawEnd;
        }

        // 尝试 Redis 缓存（cache.type=none 时跳过）
        if (cacheService != null) {
            String cacheKey = "stats:aicall:" + tenantId + ":" + hash(start) + ":" + hash(end);
            String cached = cacheService.getOrCompute(cacheKey, Duration.ofMinutes(5), () -> {
                AiCallStatsVo vo = doStats(tenantId, start, end);
                try {
                    return objectMapper.writeValueAsString(vo);
                } catch (Exception e) {
                    log.warn("[AiCallLog] 统计序列化失败: {}", e.getMessage());
                    return null;
                }
            });
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, AiCallStatsVo.class);
                } catch (Exception e) {
                    log.warn("[AiCallLog] 缓存反序列化失败，降级直查: {}", e.getMessage());
                }
            }
        }
        return doStats(tenantId, start, end);
    }

    /** 实际数据库统计聚合 */
    private AiCallStatsVo doStats(Long tenantId, String start, String end) {
        AiCallStatsVo vo = new AiCallStatsVo();
        // 概览
        Map<String, Object> ov = mapper.statsOverview(tenantId, start, end);
        if (ov != null) {
            vo.setTotalCalls(toLong(ov.get("calls")));
            vo.setSuccessCount(toLong(ov.get("successCount")));
            vo.setFailedCount(toLong(ov.get("failedCount")));
            vo.setTotalTokens(toLong(ov.get("tokens")));
            vo.setPromptTokens(toLong(ov.get("promptTokens")));
            vo.setCompletionTokens(toLong(ov.get("completionTokens")));
            vo.setTotalCost(toBigDecimal(ov.get("cost")));
            vo.setAvgDurationMs(toLong(ov.get("avgDuration")));
        }
        // 按模型
        List<AiCallStatsVo.ItemStat> byModel = new ArrayList<>();
        for (Map<String, Object> row : mapper.statsByModel(tenantId, start, end)) {
            byModel.add(new AiCallStatsVo.ItemStat(
                    (String) row.get("dim"), null,
                    toLong(row.get("calls")), toLong(row.get("tokens")), toBigDecimal(row.get("cost"))));
        }
        vo.setByModel(byModel);
        // 按用户
        List<AiCallStatsVo.ItemStat> byUser = new ArrayList<>();
        for (Map<String, Object> row : mapper.statsByUser(tenantId, start, end)) {
            byUser.add(new AiCallStatsVo.ItemStat(
                    (String) row.get("dim"), toLongObj(row.get("userId")),
                    toLong(row.get("calls")), toLong(row.get("tokens")), toBigDecimal(row.get("cost"))));
        }
        vo.setByUser(byUser);
        // 按日期
        List<AiCallStatsVo.DailyStat> byDay = new ArrayList<>();
        for (Map<String, Object> row : mapper.statsByDay(tenantId, start, end)) {
            Object dayObj = row.get("day");
            byDay.add(new AiCallStatsVo.DailyStat(
                    dayObj == null ? "" : dayObj.toString(),
                    toLong(row.get("calls")), toLong(row.get("tokens")), toBigDecimal(row.get("cost"))));
        }
        vo.setByDay(byDay);
        return vo;
    }

    /** 字符串摘要（用于缓存 key，避免特殊字符） */
    private static String hash(String s) {
        if (s == null) return "null";
        return Integer.toHexString(s.hashCode());
    }

    private static Long currentTenantId() {
        Long id = TenantContext.getTenantId();
        return id == null ? TenantContext.PLATFORM_TENANT_ID : id;
    }

    /** Map 值转 long（兼容 Number 各子类型与 null） */
    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Long toLongObj(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
