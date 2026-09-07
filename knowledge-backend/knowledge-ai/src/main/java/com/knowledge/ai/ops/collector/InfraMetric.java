package com.knowledge.ai.ops.collector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 基础设施指标埋点注解。
 * <p>加在方法上，由 {@link InfraMetricAspect} 环绕拦截：自动计时 + 异常标记失败 + 异步写 infra_metric。
 * <p>用法：{@code @InfraMetric(resource = MetricResource.MILVUS_SEARCH)} 或直接写字符串。
 *
 * @author: lxcechoo@gmail.com
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InfraMetric {

    /** 资源名，对齐 AlertService.source：milvus:search / es:search */
    String resource();

    /** 动作，默认 search */
    String action() default "search";
}
