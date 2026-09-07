package com.knowledge.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * <p>标注于 Controller 方法上，由 {@code OperLogAspect} 环绕拦截，
 * 异步记录请求参数、响应结果、耗时、操作人、IP 等到 sys_oper_log 表。
 * <p>设计原因：将日志采集与业务逻辑解耦，声明式标注即可审计，符合 AOP 横切关注点分离原则。
 *
 * @see BusinessType
 *
 * @author: lxcechoo@gmail.com
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 模块标题，如"知识库管理" */
    String title() default "";

    /** 业务类型，默认 OTHER */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否记录请求参数（默认 true）；含敏感信息的接口可关闭 */
    boolean recordParam() default true;

    /** 是否记录响应结果（默认 true）；大响应体接口可关闭 */
    boolean recordResult() default true;
}
