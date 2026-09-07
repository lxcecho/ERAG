package com.knowledge.ai.ops.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * {@link InfraMetric} 注解 AOP 切面：环绕拦截，自动计时 + 异常标记失败 + 异步写 infra_metric。
 * <p>设计：与 {@link MetricsCollector} 解耦——切面仅负责捕获耗时与异常，委托 Collector 落库。
 * <p>注意：切面异常不影响主流程（catch 后仍 throw 原异常）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class InfraMetricAspect {

    private final MetricsCollector metricsCollector;

    @Around("@annotation(infraMetric)")
    public Object around(ProceedingJoinPoint pjp, InfraMetric infraMetric) throws Throwable {
        MetricsCollector.MetricTracer tracer = metricsCollector.start(infraMetric.resource(), infraMetric.action());
        try {
            Object result = pjp.proceed();
            tracer.success();
            return result;
        } catch (Throwable e) {
            tracer.failure(e);
            throw e;
        }
    }
}
