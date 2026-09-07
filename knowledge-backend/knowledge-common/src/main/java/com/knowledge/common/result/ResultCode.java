package com.knowledge.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应状态码枚举
 * <p>编码规则：
 * <ul>
 *   <li>2xx：成功</li>
 *   <li>4xx：客户端错误</li>
 *   <li>1xxx：业务错误</li>
 *   <li>5xxx：系统错误</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),

    /* 客户端错误 */
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),

    /* 业务错误 */
    PARAM_VALIDATE_FAILED(1001, "参数校验失败"),
    BUSINESS_ERROR(1002, "业务异常"),

    /* 限流与降级（Sentinel 触发） */
    RATE_LIMITED(4290, "请求过于频繁，请稍后再试"),
    SERVICE_DEGRADED(4291, "服务暂时不可用，已降级处理"),

    /* 系统错误 */
    SYSTEM_ERROR(5000, "系统异常"),
    DATABASE_ERROR(5001, "数据库异常"),
    NETWORK_ERROR(5002, "网络异常"),
    CIRCUIT_BREAKER_OPEN(5003, "上游服务熔断中，请稍后再试"),
    ALERT_TRIGGERED(5004, "系统告警已触发");

    private final int code;
    private final String message;
}
