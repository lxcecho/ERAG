package com.knowledge.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.entity.ToolMetadata;
import com.knowledge.common.context.TenantContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具执行器（门面）：编排工具调用的完整生命周期。
 * <p>
 * 执行流程：
 * <pre>
 *   1. 注册中心查找工具       → 未找到返回 TOOL_NOT_FOUND
 *   2. JSON Schema 参数校验    → 缺必填参数返回 MISSING_PARAMETER
 *   3. 权限校验               → 工具启用 + 角色授权 + KB viewer 权限（ToolPermissionChecker 统一编排）
 *   4. 超时执行（v3-3）        → per-tool timeout，专用线程池 + Future.get(timeout)，超时 cancel 中断
 *   5. 记录审计日志            → ToolCallRecorder 持久化到 agent_message（含超时/失败结果）
 * </pre>
 * <p>
 * <b>工具超时</b>（v3-3）：per-tool timeout 取自 {@link ToolMetadata#getTimeoutMs()}，
 * 无配置时用全局默认 {@code agent.tool.default-timeout-ms}。工具执行在专用线程池中异步运行，
 * 主线程 {@code Future.get(timeout)} 阻塞等待；超时则 {@code cancel(true)} 中断阻塞型工具
 * （DB/HTTP 可中断），向调用方返回超时失败（best-effort：LLM 阻塞可能不响应中断）。
 * <p>
 * <b>租户上下文</b>：在池任务内 set/clear TenantContext（保证工具内部 MyBatis-Plus 租户拦截器生效），
 * 主线程不持有租户上下文（权限校验不依赖拦截器，见 ToolPermissionChecker）。
 * <p>
 * <b>构造兼容</b>：保留 4 参构造（现有 {@code ToolExecutorTest} 零改动，默认超时 + daemon 池）；
 * Spring 用 6 参构造注入配置与专用线程池。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class ToolExecutor {

    /** 4 参构造的默认超时（无 AgentProperties 时兜底） */
    private static final long DEFAULT_TIMEOUT_MS = 60000L;

    private final ToolRegistry registry;
    private final ToolPermissionChecker permissionChecker;
    private final ToolCallRecorder recorder;
    private final ObjectMapper objectMapper;
    private final AgentProperties props;
    private final ExecutorService pool;

    /**
     * 测试/兼容构造：默认超时 + 单线程 daemon 池。
     * <p>现有 {@code ToolExecutorTest} 使用此构造，保持零改动。
     */
    public ToolExecutor(ToolRegistry registry, ToolPermissionChecker permissionChecker,
                        ToolCallRecorder recorder, ObjectMapper objectMapper) {
        this(registry, permissionChecker, recorder, objectMapper, new AgentProperties(),
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "tool-exec-test");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /**
     * Spring 主构造：注入配置与专用线程池。
     */
    @Autowired
    public ToolExecutor(ToolRegistry registry, ToolPermissionChecker permissionChecker,
                        ToolCallRecorder recorder, ObjectMapper objectMapper,
                        AgentProperties props, ExecutorService toolExecutorPool) {
        this.registry = registry;
        this.permissionChecker = permissionChecker;
        this.recorder = recorder;
        this.objectMapper = objectMapper;
        this.props = props;
        this.pool = toolExecutorPool;
    }

    /**
     * 执行工具调用（完整生命周期编排）。
     *
     * @param toolName  工具名
     * @param ctx       执行上下文（租户/用户/KB 身份）
     * @param arguments 入参（key=参数名，value=参数值）
     * @return 执行结果（成功携带数据 + token；失败携带错误信息）
     */
    public ToolResult execute(String toolName, ToolContext ctx, Map<String, Object> arguments) {
        long start = System.currentTimeMillis();

        // 1. 查找工具
        Tool tool = registry.getTool(toolName);
        if (tool == null) {
            log.warn("[ToolExecutor] 工具不存在: {}", toolName);
            return ToolResult.failure("工具不存在: " + toolName);
        }

        // 2. 参数校验（JSON Schema required 字段）
        ToolResult validationError = validateArguments(tool, arguments);
        if (validationError != null) {
            return validationError;
        }

        // 3. 权限校验（含工具启用 + 角色授权 + KB 权限，由 ToolPermissionChecker 统一编排；
        //    拒绝时抛 ToolException，此处捕获转失败结果并记审计）
        try {
            permissionChecker.check(tool, ctx);
        } catch (ToolException e) {
            log.warn("[ToolExecutor] 权限校验失败 tool={} user={} kb={}: {}",
                    toolName, ctx.getUserId(), ctx.getKbId(), e.getMessage());
            return ToolResult.failure(e.getMessage());
        }

        // 4. 超时执行：per-tool timeout 取自 DB 元数据（无则全局默认），专用线程池 + Future.get(timeout)
        ToolMetadata meta = registry.getMetadata(ctx.getTenantId(), toolName);
        long timeout = resolveTimeout(meta);
        Future<ToolResult> future = pool.submit(() -> {
            TenantContext.setTenantId(ctx.getTenantId());
            try {
                log.info("[ToolExecutor] 执行工具={} 用户={} KB={} 参数={}",
                        toolName, ctx.getUserId(), ctx.getKbId(), arguments.keySet());
                return tool.execute(ctx, arguments);
            } catch (ToolException e) {
                log.warn("[ToolExecutor] 工具={} 执行异常(业务): code={} msg={}",
                        toolName, e.getErrorCode(), e.getMessage());
                return ToolResult.failure(e.getMessage());
            } catch (Exception e) {
                log.error("[ToolExecutor] 工具={} 执行异常(系统)", toolName, e);
                return ToolResult.failure("工具执行异常: " + e.getMessage());
            } finally {
                TenantContext.clear();
            }
        });

        ToolResult result;
        try {
            result = future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            log.warn("[ToolExecutor] 工具={} 执行超时({}ms)，已中断", toolName, timeout);
            result = ToolResult.failure("工具 [" + toolName + "] 执行超时(" + timeout + "ms)");
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            result = ToolResult.failure("工具执行异常: " + cause.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            result = ToolResult.failure("工具执行被中断");
        }

        long duration = System.currentTimeMillis() - start;

        // 6. 记录审计（即使失败/超时也记录，便于排查）
        recorder.record(ctx, toolName, arguments, result, duration);

        log.info("[ToolExecutor] 工具={} 完成 success={} 耗时={}ms token={}",
                toolName, result.isSuccess(), duration, result.getTokensUsed());
        return result;
    }

    /** 解析 per-tool 超时：DB 元数据优先，无则全局默认 */
    private long resolveTimeout(ToolMetadata meta) {
        if (meta != null && meta.getTimeoutMs() != null) {
            return meta.getTimeoutMs();
        }
        long configured = props.getTool() != null ? props.getTool().getDefaultTimeoutMs() : 0;
        return configured > 0 ? configured : DEFAULT_TIMEOUT_MS;
    }

    /**
     * JSON Schema 参数校验：解析 schema 的 required 数组，校验必填参数存在。
     * <p>简化校验（不引入第三方 JSON Schema 验证库）：仅校验 required 字段是否存在，
     * 类型校验由各工具在 execute 内自行处理。
     *
     * @return 校验失败返回 failure 结果，通过返回 null
     */
    private ToolResult validateArguments(Tool tool, Map<String, Object> arguments) {
        try {
            JsonNode schema = objectMapper.readTree(tool.parametersJsonSchema());
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                for (JsonNode field : required) {
                    String fieldName = field.asText();
                    if (!arguments.containsKey(fieldName) || arguments.get(fieldName) == null) {
                        String msg = "工具 [" + tool.name() + "] 缺少必填参数: " + fieldName;
                        log.warn("[ToolExecutor] {}", msg);
                        return ToolResult.failure(msg);
                    }
                }
            }
        } catch (Exception e) {
            // Schema 解析失败不阻断执行（降级为跳过校验）
            log.debug("[ToolExecutor] 工具={} Schema 解析失败，跳过参数校验: {}",
                    tool.name(), e.getMessage());
        }
        return null;
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
