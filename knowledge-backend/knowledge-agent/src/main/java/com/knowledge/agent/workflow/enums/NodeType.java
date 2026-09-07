package com.knowledge.agent.workflow.enums;

/**
 * Workflow 节点类型（策略路由依据）。
 * <ul>
 *   <li>{@link #START}  — 流程入口（隐式，仅标记起点）</li>
 *   <li>{@link #TOOL}   — 调用注册中心的 Tool（复用 ToolExecutor，如 knowledge_search/report_generate）</li>
 *   <li>{@link #LLM}    — 调用大模型生成文本（prompt 模板 + 变量插值）</li>
 *   <li>{@link #HUMAN}  — 人工审批门禁：暂停流程，等待 approve/reject 后恢复</li>
 *   <li>{@link #END}    — 流程出口，其产物作为流程最终结果</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
public enum NodeType {
    START,
    TOOL,
    LLM,
    HUMAN,
    END
}
