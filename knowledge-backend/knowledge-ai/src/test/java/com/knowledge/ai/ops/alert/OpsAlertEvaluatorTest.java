/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.ops.alert;

import com.knowledge.ai.calllog.mapper.AiCallLogMapper;
import com.knowledge.ai.health.AiCallHealthTracker;
import com.knowledge.ai.ops.entity.AlertRule;
import com.knowledge.ai.ops.mapper.AlertRuleMapper;
import com.knowledge.ai.ops.mapper.InfraMetricMapper;
import com.knowledge.common.alert.AlertLevel;
import com.knowledge.common.alert.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 运维告警评估器单测。
 * <p>风格：plain JUnit5 + Mockito mock()，SUT 用 new 构造传 mock（@RequiredArgsConstructor 全参）。
 * <p>覆盖：
 * <ul>
 *   <li>{@code matchOperator} 四操作符（GT/GTE/LT/LTE）+ NaN 永不匹配 + null/未知操作符</li>
 *   <li>{@code evaluate} 资源路由（llm:* → ai_call_log / infra → infra_metric）</li>
 *   <li>阈值触发 / 未触发 / NaN 不告警</li>
 *   <li>单规则评估异常不影响后续规则</li>
 * </ul>
 * <p>注意：{@code matchOperator} 为包级静态方法，同包测试可直接调用。
 */
class OpsAlertEvaluatorTest {

    private AlertRuleMapper alertRuleMapper;
    private AlertService alertService;
    private AiCallHealthTracker healthTracker;
    private InfraMetricMapper infraMetricMapper;
    private AiCallLogMapper aiCallLogMapper;
    private OpsAlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        alertRuleMapper = mock(AlertRuleMapper.class);
        alertService = mock(AlertService.class);
        healthTracker = mock(AiCallHealthTracker.class);
        infraMetricMapper = mock(InfraMetricMapper.class);
        aiCallLogMapper = mock(AiCallLogMapper.class);
        evaluator = new OpsAlertEvaluator(alertRuleMapper, alertService, healthTracker,
                infraMetricMapper, aiCallLogMapper);
    }

    /** 构造告警规则（tenantId=1，level=WARN，enabled=1） */
    private AlertRule rule(long id, String name, String resource, String metric,
                           String operator, double threshold, Integer window) {
        AlertRule r = new AlertRule();
        r.setId(id);
        r.setTenantId(1L);
        r.setName(name);
        r.setResource(resource);
        r.setMetric(metric);
        r.setOperator(operator);
        r.setThreshold(BigDecimal.valueOf(threshold));
        r.setWindowMinutes(window);
        r.setLevel("WARN");
        r.setEnabled(1);
        return r;
    }

    // ==================== matchOperator 四操作符 ====================

    @Test
    void matchOperator_GT_大于阈值才匹配() {
        assertTrue(OpsAlertEvaluator.matchOperator("GT", 10, 5));
        assertFalse(OpsAlertEvaluator.matchOperator("GT", 5, 5));
        assertFalse(OpsAlertEvaluator.matchOperator("GT", 3, 5));
    }

    @Test
    void matchOperator_GTE_大于等于匹配() {
        assertTrue(OpsAlertEvaluator.matchOperator("GTE", 10, 5));
        assertTrue(OpsAlertEvaluator.matchOperator("GTE", 5, 5));
        assertFalse(OpsAlertEvaluator.matchOperator("GTE", 3, 5));
    }

    @Test
    void matchOperator_LT_小于阈值才匹配() {
        assertTrue(OpsAlertEvaluator.matchOperator("LT", 3, 5));
        assertFalse(OpsAlertEvaluator.matchOperator("LT", 5, 5));
        assertFalse(OpsAlertEvaluator.matchOperator("LT", 10, 5));
    }

    @Test
    void matchOperator_LTE_小于等于匹配() {
        assertTrue(OpsAlertEvaluator.matchOperator("LTE", 3, 5));
        assertTrue(OpsAlertEvaluator.matchOperator("LTE", 5, 5));
        assertFalse(OpsAlertEvaluator.matchOperator("LTE", 10, 5));
    }

    @Test
    void matchOperator_NaN_永不匹配() {
        assertFalse(OpsAlertEvaluator.matchOperator("GT", Double.NaN, 5));
        assertFalse(OpsAlertEvaluator.matchOperator("GTE", Double.NaN, 0));
        assertFalse(OpsAlertEvaluator.matchOperator("LT", Double.NaN, 100));
        assertFalse(OpsAlertEvaluator.matchOperator("LTE", Double.NaN, 0));
    }

    @Test
    void matchOperator_null或未知操作符_不匹配() {
        assertFalse(OpsAlertEvaluator.matchOperator(null, 10, 5));
        assertFalse(OpsAlertEvaluator.matchOperator("UNKNOWN", 10, 5));
        assertFalse(OpsAlertEvaluator.matchOperator("eq", 10, 10));
    }

    // ==================== evaluate 路由 + 触发 ====================

    @Test
    void evaluate_无启用规则_不评估不告警() {
        when(alertRuleMapper.listAllEnabled()).thenReturn(List.of());
        evaluator.evaluate();
        verifyNoInteractions(alertService, infraMetricMapper, aiCallLogMapper, healthTracker);
    }

    @Test
    void evaluate_llm调用次数超阈值_触发告警() {
        when(alertRuleMapper.listAllEnabled()).thenReturn(List.of(
                rule(1L, "llm-calls", "llm:chat", "calls", "GT", 100, 5)));
        when(aiCallLogMapper.countInWindow(1L, 5)).thenReturn(150L);

        evaluator.evaluate();

        // detail 格式：指标 calls=150.00 GT 阈值 100.00（窗口 5 分钟）
        verify(alertService).alert(eq(AlertLevel.WARN), eq("llm:chat"),
                contains("llm-calls"), contains("calls=150.00"), isNull());
    }

    @Test
    void evaluate_milvus延迟P95超阈值_路由infraMetric_触发告警() {
        when(alertRuleMapper.listAllEnabled()).thenReturn(List.of(
                rule(2L, "milvus-latency", "milvus:search", "latency_p95", "GT", 500, 5)));
        when(infraMetricMapper.latencyP95InWindow(1L, "milvus:search", 5)).thenReturn(800L);

        evaluator.evaluate();

        verify(alertService).alert(eq(AlertLevel.WARN), eq("milvus:search"),
                contains("milvus-latency"), contains("latency_p95=800.00"), isNull());
        // infra 资源不应走 aiCallLogMapper
        verify(aiCallLogMapper, never()).countInWindow(anyLong(), anyInt());
    }

    @Test
    void evaluate_es错误率超阈值_路由infraMetric_触发告警() {
        when(alertRuleMapper.listAllEnabled()).thenReturn(List.of(
                rule(3L, "es-error", "es:search", "error_rate", "GTE", 10, 5)));
        when(infraMetricMapper.errorRateInWindow(1L, "es:search", 5)).thenReturn(25.5);

        evaluator.evaluate();

        verify(alertService).alert(eq(AlertLevel.WARN), eq("es:search"),
                contains("es-error"), contains("error_rate=25.50"), isNull());
    }

    @Test
    void evaluate_llmToken消耗超阈值_路由aiCallLog_触发告警() {
        when(alertRuleMapper.listAllEnabled()).thenReturn(List.of(
                rule(4L, "llm-token", "llm:chat", "token_usage", "GT", 100000, 5)));
        when(aiCallLogMapper.sumTokensInWindow(1L, 5)).thenReturn(250000L);

        evaluator.evaluate();

        verify(alertService).alert(eq(AlertLevel.WARN), eq("llm:chat"),
                anyString(), contains("token_usage=250000.00"), isNull());
    }

    // ==================== 未触发 / 异常隔离 ====================

    @Test
    void evaluate_未超阈值_不告警() {
        when(alertRuleMapper.listAllEnabled()).thenReturn(List.of(
                rule(1L, "llm-calls", "llm:chat", "calls", "GT", 100, 5)));
        when(aiCallLogMapper.countInWindow(1L, 5)).thenReturn(50L);

        evaluator.evaluate();

        verify(alertService, never()).alert(any(), any(), any(), any(), any());
    }

    @Test
    void evaluate_不支持的指标返回NaN_不告警() {
        when(alertRuleMapper.listAllEnabled()).thenReturn(List.of(
                rule(1L, "bad-metric", "llm:chat", "unknown_metric", "GT", 100, 5)));

        evaluator.evaluate();

        verify(alertService, never()).alert(any(), any(), any(), any(), any());
    }

    @Test
    void evaluate_windowMinutes为null_兜底5分钟窗口() {
        when(alertRuleMapper.listAllEnabled()).thenReturn(List.of(
                rule(1L, "no-window", "llm:chat", "calls", "GT", 100, null)));
        when(aiCallLogMapper.countInWindow(1L, 5)).thenReturn(200L);

        evaluator.evaluate();

        verify(aiCallLogMapper).countInWindow(1L, 5);
        verify(alertService).alert(any(), any(), any(), any(), any());
    }

    @Test
    void evaluate_单规则评估异常_不影响后续规则() {
        AlertRule badRule = rule(1L, "bad-rule", "llm:chat", "calls", "GT", 100, 5);
        AlertRule goodRule = rule(2L, "good-rule", "milvus:search", "latency_p95", "GT", 500, 5);
        when(alertRuleMapper.listAllEnabled()).thenReturn(List.of(badRule, goodRule));
        // 第一条规则取数抛异常（模拟 DB 故障）
        when(aiCallLogMapper.countInWindow(1L, 5)).thenThrow(new RuntimeException("db error"));
        when(infraMetricMapper.latencyP95InWindow(1L, "milvus:search", 5)).thenReturn(800L);

        evaluator.evaluate();

        // 第一条异常被吞，第二条仍评估并告警
        verify(alertService).alert(eq(AlertLevel.WARN), eq("milvus:search"),
                contains("good-rule"), anyString(), isNull());
    }
}
