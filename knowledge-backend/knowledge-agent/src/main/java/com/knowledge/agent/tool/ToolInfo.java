package com.knowledge.agent.tool;

import com.knowledge.agent.entity.ToolMetadata;

/**
 * 工具发现运行时视图：合并代码层 {@link Tool} 与 DB 层 {@link ToolMetadata}。
 * <p>供工具发现接口与 LLM function-calling 声明使用：仅含启用工具，按 sort_order 排序。
 * <p>{@code codePresent} 标识是否存在代码层实现（DB 有元数据但无 Bean 时为 false，发现接口可标记"未部署"）。
 *
 * @author: lxcechoo@gmail.com
 */
public record ToolInfo(
        String name,
        String displayName,
        String description,
        String category,
        String version,
        boolean authRequired,
        Integer timeoutMs,
        boolean enabled,
        boolean codePresent
) {
}
