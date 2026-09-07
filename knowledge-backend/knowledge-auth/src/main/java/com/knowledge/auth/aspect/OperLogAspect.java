package com.knowledge.auth.aspect;

import cn.hutool.json.JSONUtil;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.system.operlog.entity.SysOperLog;
import com.knowledge.system.operlog.service.SysOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 操作日志切面：环绕拦截 @{@link OperLog} 注解的 Controller 方法。
 * <p>设计原因：
 * <ul>
 *   <li>同步采集请求数据与操作人（@Async 线程内 RequestContextHolder 不传递，必须在切面线程采集）</li>
 *   <li>异步落库到 sys_oper_log，不阻塞业务请求</li>
 *   <li>放在 knowledge-auth 模块：可访问 {@link SecurityUtils}（当前用户）与 {@link SysOperLogService}（auth 依赖 system）</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private static final int MAX_TEXT_LEN = 2000;

    private final SysOperLogService sysOperLogService;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        // 切面线程内同步采集：请求信息与操作人（异步线程拿不到 RequestContext）
        HttpServletRequest request = currentRequest();
        String username = safeUsername();
        long start = System.currentTimeMillis();

        SysOperLog logEntity = new SysOperLog();
        logEntity.setTitle(operLog.title());
        logEntity.setBusinessType(operLog.businessType().getCode());
        logEntity.setMethod(joinPoint.getSignature().getDeclaringTypeName() + "."
                + joinPoint.getSignature().getName());
        logEntity.setRequestUrl(request != null ? request.getRequestURI() : "");
        logEntity.setOperIp(request != null ? resolveIp(request) : "");
        logEntity.setOperUser(username);
        if (operLog.recordParam()) {
            logEntity.setRequestParam(serializeArgs(joinPoint.getArgs()));
        }

        Object result = null;
        try {
            result = joinPoint.proceed();
            logEntity.setStatus(0);
            if (operLog.recordResult()) {
                logEntity.setResponseResult(serializeResult(result));
            }
            return result;
        } catch (Throwable e) {
            logEntity.setStatus(1);
            logEntity.setErrorMsg(truncate(e.getMessage()));
            throw e;
        } finally {
            logEntity.setCostTime(System.currentTimeMillis() - start);
            logEntity.setCreateTime(LocalDateTime.now());
            // 跨 Bean 代理调用，触发 @Async 异步落库
            sysOperLogService.saveAsync(logEntity);
        }
    }

    /** 安全获取当前用户名（登录接口此时未认证，返回空） */
    private String safeUsername() {
        try {
            return SecurityUtils.currentUsername();
        } catch (Exception e) {
            return "";
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    /** 解析真实 IP：优先代理头 X-Forwarded-For */
    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取首个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /** 序列化请求参数：过滤不可序列化类型（文件流等） */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Object> filtered = Arrays.stream(args)
                .filter(a -> !(a instanceof MultipartFile)
                        && !(a instanceof HttpServletRequest)
                        && !(a instanceof byte[]))
                .toList();
        try {
            return truncate(JSONUtil.toJsonStr(filtered));
        } catch (Exception e) {
            return truncate("参数序列化失败: " + e.getMessage());
        }
    }

    private String serializeResult(Object result) {
        if (result == null) {
            return "";
        }
        try {
            return truncate(JSONUtil.toJsonStr(result));
        } catch (Exception e) {
            return truncate("结果序列化失败: " + e.getMessage());
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_TEXT_LEN ? text.substring(0, MAX_TEXT_LEN) : text;
    }
}
