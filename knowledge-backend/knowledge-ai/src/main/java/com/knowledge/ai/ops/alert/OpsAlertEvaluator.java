package com.knowledge.ai.ops.alert;

import com.knowledge.ai.calllog.mapper.AiCallLogMapper;
import com.knowledge.ai.health.AiCallHealthTracker;
import com.knowledge.ai.ops.entity.AlertRule;
import com.knowledge.ai.ops.mapper.AlertRuleMapper;
import com.knowledge.ai.ops.mapper.InfraMetricMapper;
import com.knowledge.common.alert.AlertLevel;
import com.knowledge.common.alert.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 运维告警评估器：定时（默认每 5 分钟）扫描启用规则，按 {@code resource+metric} 路由取数，
 * 超阈值则复用 {@link AlertService#alert} 发送告警（冷却去重 + Webhook）。
 * <p>
 * 资源路由：
 * <ul>
 *   <li>{@code llm:*} → ai_call_log：calls / token_usage 走 SQL 窗口统计；
 *       error_rate 走 {@link AiCallHealthTracker}（内存滑动窗口，零 SQL，1 分钟窗口）</li>
 *   <li>{@code milvus:} / {@code es:} / {@code mq:} → infra_metric：
 *       calls / error_rate / latency_p95 均走 SQL 窗口统计</li>
 * </ul>
 * <p>评估失败的单条规则仅 warn，不影响其它规则；定时任务线程无 TenantContext，
 * 规则的 tenantId 由 {@link AlertRuleMapper#listAllEnabled} 跨租户读出后逐条传入。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpsAlertEvaluator {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertService alertService;
    private final AiCallHealthTracker healthTracker;
    private final InfraMetricMapper infraMetricMapper;
    private final AiCallLogMapper aiCallLogMapper;

    /**
     * 定时评估：cron 由 {@code ops.alert.eval-cron} 配置，默认每 5 分钟。
     */
    @Scheduled(cron = "${ops.alert.eval-cron:0 */5 * * * ?}")
    public void evaluate() {
        List<AlertRule> rules = alertRuleMapper.listAllEnabled();
        if (rules == null || rules.isEmpty()) {
            return;
        }
        log.debug("[OpsAlert] 评估 {} 条规则", rules.size());
        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.warn("[OpsAlert] 规则评估失败 id={} name={}: {}", rule.getId(), rule.getName(), e.getMessage());
            }
        }
    }

    private void evaluateRule(AlertRule rule) {
        double actual = resolveMetric(rule);
        double threshold = rule.getThreshold() != null ? rule.getThreshold().doubleValue() : 0;
        if (matchOperator(rule.getOperator(), actual, threshold)) {
            String detail = String.format("指标 %s=%.2f %s 阈值 %.2f（窗口 %d 分钟）",
                    rule.getMetric(), actual, rule.getOperator(), threshold, rule.getWindowMinutes());
            alertService.alert(parseLevel(rule.getLevel()), rule.getResource(),
                    "规则触发:" + rule.getName(), detail, null);
            log.info("[OpsAlert] 规则触发 id={} name={} resource={} {}", rule.getId(), rule.getName(),
                    rule.getResource(), detail);
        }
    }

    /** 按 resource 前缀路由取数 */
    private double resolveMetric(AlertRule rule) {
        String resource = rule.getResource();
        String metric = rule.getMetric();
        int window = rule.getWindowMinutes() != null ? rule.getWindowMinutes() : 5;
        Long tid = rule.getTenantId();

        if (resource != null && resource.startsWith("llm:")) {
            // llm:chat → ai_call_log / healthTracker
            return switch (metric) {
                case "calls" -> aiCallLogMapper.countInWindow(tid, window);
                case "error_rate" -> healthTracker.getStats("llm").errorRate() * 100;
                case "token_usage" -> aiCallLogMapper.sumTokensInWindow(tid, window);
                default -> {
                    log.warn("[OpsAlert] llm 资源不支持指标 metric={}（支持 calls/error_rate/token_usage）", metric);
                    yield Double.NaN;
                }
            };
        }
        // infra 资源（milvus:/es:/mq:）→ infra_metric
        return switch (metric) {
            case "calls" -> infraMetricMapper.countByResourceInWindow(tid, resource, window);
            case "error_rate" -> infraMetricMapper.errorRateInWindow(tid, resource, window);
            case "latency_p95" -> infraMetricMapper.latencyP95InWindow(tid, resource, window);
            default -> {
                log.warn("[OpsAlert] infra 资源不支持指标 metric={}（支持 calls/error_rate/latency_p95）", metric);
                yield Double.NaN;
            }
        };
    }

    /** 比较符判定（NaN 永不匹配） */
    static boolean matchOperator(String operator, double actual, double threshold) {
        if (Double.isNaN(actual)) {
            return false;
        }
        if (operator == null) {
            return false;
        }
        return switch (operator) {
            case "GT" -> actual > threshold;
            case "GTE" -> actual >= threshold;
            case "LT" -> actual < threshold;
            case "LTE" -> actual <= threshold;
            default -> false;
        };
    }

    private static AlertLevel parseLevel(String level) {
        if (level == null) {
            return AlertLevel.WARN;
        }
        try {
            return AlertLevel.valueOf(level);
        } catch (IllegalArgumentException e) {
            return AlertLevel.WARN;
        }
    }
}
