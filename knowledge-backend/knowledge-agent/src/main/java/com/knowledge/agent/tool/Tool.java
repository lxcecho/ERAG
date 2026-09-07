package com.knowledge.agent.tool;

import java.util.Map;

/**
 * Agent 工具 SPI 接口。
 * <p>
 * 设计原则：
 * <ul>
 *   <li><b>SPI 扩展</b>：实现类标注 {@code @Component} 即自动注册到 {@link ToolRegistry}，
 *       新增工具零配置接入（符合"对扩展开放、对修改封闭"原则）；</li>
 *   <li><b>JSON Schema 声明</b>：每个工具通过 {@link #parametersJsonSchema()} 声明入参结构，
 *       供 LLM 函数调用理解接口 + {@code ToolExecutor} 做必填参数校验；</li>
 *   <li><b>权限标记</b>：{@link #authRequired()} 标识是否需要 KB 级权限校验，
 *       {@code ToolPermissionChecker} 据此决定是否调用 {@code KbPermissionService.checkViewer}；</li>
 *   <li><b>无状态</b>：工具本身无状态，执行所需的租户/用户/KB 身份通过 {@link ToolContext} 传入，
 *       产物通过 {@link ToolResult} 返回，线程安全。</li>
 * </ul>
 *
 * @see ToolRegistry
 * @see ToolExecutor
 *
 * @author: lxcechoo@gmail.com
 */
public interface Tool {

    /**
     * 工具唯一标识（snake_case，供 LLM function calling 与 {@link ToolRegistry} 索引）。
     *
     * @return 工具名，如 {@code "knowledge_search"}
     */
    String name();

    /**
     * 人类可读描述（供 LLM 理解工具用途，决定是否调用）。
     *
     * @return 工具描述
     */
    String description();

    /**
     * 入参 JSON Schema（声明参数类型、必填项、默认值，供 LLM 函数调用 + 框架校验）。
     * <p>示例：
     * <pre>{@code
     * {
     *   "type": "object",
     *   "properties": {
     *     "query": {"type": "string", "description": "检索查询"}
     *   },
     *   "required": ["query"]
     * }
     * }</pre>
     *
     * @return JSON Schema 字符串
     */
    String parametersJsonSchema();

    /**
     * 是否需要 KB 级权限校验。
     * <p>{@code true}：执行前校验用户对 kbId 的 viewer 权限（防越权检索）；
     * {@code false}：不涉及 KB 数据的工具（如纯 LLM 报告生成），跳过权限校验。
     *
     * @return 是否需要权限校验
     */
    boolean authRequired();

    /**
     * 执行工具。
     * <p>框架保证：调用前已完成权限校验与参数校验，调用后记录审计日志；
     * 实现方只需关注业务逻辑，异常向上抛出由 {@link ToolExecutor} 统一捕获。
     *
     * @param ctx       执行上下文（租户/用户/KB/任务身份）
     * @param arguments 入参（key=参数名，value=参数值，由 JSON 反序列化得到）
     * @return 执行结果（成功携带数据 + token 消耗；失败携带错误信息）
     */
    ToolResult execute(ToolContext ctx, Map<String, Object> arguments);
}
