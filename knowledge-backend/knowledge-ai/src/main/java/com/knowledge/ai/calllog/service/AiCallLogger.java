package com.knowledge.ai.calllog.service;

import com.knowledge.ai.calllog.entity.AiCallLog;
import com.knowledge.ai.calllog.mapper.AiCallLogMapper;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.Executor;

/**
 * AI 调用日志记录器：以 {@link Tracer} 形式埋点，记录每次模型调用并异步落库。
 * <p>使用方式：
 * <pre>
 *   AiCallLogger.Tracer tracer = aiCallLogger.trace("rag_chat", "CHAT", modelName);
 *   try {
 *       ChatResponse resp = ...;
 *       tracer.success(promptTokens, completionTokens);   // 成功：记录 token+耗时+费用
 *   } catch (Exception e) {
 *       tracer.failure(e);                                // 失败：记录状态+错误
 *       throw e;
 *   }
 * </pre>
 * <p>身份捕获时机：在 {@code trace()} 调用线程捕获 userId/tenantId（避免异步线程 SecurityContext 丢失）；
 * Agent/Workflow 等异步场景须用显式身份重载 {@code trace(...,userId,username,tenantId)}。
 * <p>异步落库：通过 {@code aiCallLogExecutor}（TTL 包装）提交，不阻塞调用线程；
 * 日志写入失败仅 warn 不抛出，避免影响主流程。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class AiCallLogger {

    private final AiCallLogMapper mapper;
    private final CostCalculator costCalculator;
    private final Executor aiCallLogExecutor;

    public AiCallLogger(AiCallLogMapper mapper,
                        CostCalculator costCalculator,
                        @Qualifier("aiCallLogExecutor") Executor aiCallLogExecutor) {
        this.mapper = mapper;
        this.costCalculator = costCalculator;
        this.aiCallLogExecutor = aiCallLogExecutor;
    }

    /**
     * 请求线程埋点：从 SecurityUtils + TenantContext 捕获身份。
     * <p>适用于 RAG 对话、Prompt 测试等在 HTTP 请求线程发起的模型调用。
     */
    public Tracer trace(String module, String bizType, String modelName) {
        return new Tracer(this, costCalculator, module, bizType, modelName,
                safeCurrentUserId(), safeCurrentUsername(), TenantContext.getTenantId());
    }

    /**
     * 异步/Agent 埋点：显式传入身份。
     * <p>适用于 AgentExecutor/WorkflowExecutor 等线程池场景（SecurityContext 不可用）。
     */
    public Tracer trace(String module, String bizType, String modelName,
                        Long userId, String username, Long tenantId) {
        return new Tracer(this, costCalculator, module, bizType, modelName, userId, username, tenantId);
    }

    /** 异步写入日志（程序式异步，避免 @Async 自调用代理失效） */
    void record(AiCallLog callLog) {
        try {
            aiCallLogExecutor.execute(() -> {
                try {
                    mapper.insert(callLog);
                } catch (Exception e) {
                    log.warn("[AiCallLog] 写入失败 module={} model={} err={}",
                            callLog.getModule(), callLog.getModelName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("[AiCallLog] 提交异步任务失败: {}", e.getMessage());
        }
    }

    /** 安全获取当前用户ID（未登录/系统调用返回 null） */
    private static Long safeCurrentUserId() {
        try {
            return SecurityUtils.currentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 安全获取当前用户名（未登录返回 null） */
    private static String safeCurrentUsername() {
        try {
            return SecurityUtils.currentUsername();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 调用追踪器：捕获起始时刻身份，在 success/failure 时计算耗时/费用并异步落库。
     */
    public static class Tracer {
        private final AiCallLogger logger;
        private final CostCalculator costCalculator;
        private final String module;
        private final String bizType;
        private final String modelName;
        private final Long userId;
        private final String username;
        private final Long tenantId;
        private final long startMs;

        Tracer(AiCallLogger logger, CostCalculator costCalculator,
               String module, String bizType, String modelName,
               Long userId, String username, Long tenantId) {
            this.logger = logger;
            this.costCalculator = costCalculator;
            this.module = module;
            this.bizType = bizType;
            this.modelName = modelName;
            this.userId = userId;
            this.username = username;
            this.tenantId = tenantId;
            this.startMs = System.currentTimeMillis();
        }

        /** 成功：使用 trace 时的模型名记录 token */
        public void success(int promptTokens, int completionTokens) {
            finish(modelName, promptTokens, completionTokens, "SUCCESS", null);
        }

        /** 成功：用响应中的实际模型名覆盖（Spring AI 响应可取实际模型） */
        public void success(String actualModel, int promptTokens, int completionTokens) {
            finish(actualModel != null ? actualModel : modelName, promptTokens, completionTokens, "SUCCESS", null);
        }

        /** 失败：记录异常（token 记 0） */
        public void failure(Throwable e) {
            String msg = e.getMessage();
            if (msg != null && msg.length() > 500) {
                msg = msg.substring(0, 500);
            }
            finish(modelName, 0, 0, "FAILED", msg);
        }

        private void finish(String model, int promptTokens, int completionTokens, String status, String errorMsg) {
            AiCallLog callLog = new AiCallLog();
            callLog.setTenantId(tenantId != null ? tenantId : TenantContext.PLATFORM_TENANT_ID);
            callLog.setUserId(userId);
            callLog.setUsername(username != null ? username : "");
            callLog.setModule(module);
            callLog.setBizType(bizType);
            callLog.setModelName(model != null ? model : "unknown");
            callLog.setPromptTokens(promptTokens);
            callLog.setCompletionTokens(completionTokens);
            callLog.setTotalTokens(promptTokens + completionTokens);
            callLog.setDurationMs((int) (System.currentTimeMillis() - startMs));
            BigDecimal cost = costCalculator.calculate(callLog.getModelName(), promptTokens, completionTokens);
            callLog.setCost(cost);
            callLog.setStatus(status);
            callLog.setErrorMsg(errorMsg);
            logger.record(callLog);
        }
    }
}
