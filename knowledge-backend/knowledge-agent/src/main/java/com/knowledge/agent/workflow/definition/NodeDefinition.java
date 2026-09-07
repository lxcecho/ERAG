package com.knowledge.agent.workflow.definition;

import com.knowledge.agent.workflow.enums.NodeType;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点定义（流程定义 JSON 内的单个节点，非 DB 实体）。
 * <p>
 * 设计为"线性 + 跳转"路由模型：每个节点通过 {@link #next} 指向后继节点ID（默认指向列表下一节点），
 * HUMAN 节点驳回时走 {@link #rejectNext}（默认 null=终止并标记 CANCELED）。该模型覆盖企业可控流程的
 * 主流形态（顺序 + 审批分支），避免引入完整 DAG 引擎的复杂度。
 * <p>
 * 变量传递：节点执行后产物写入上下文 key={@link #outputKey}，下游节点通过 ${var} 引用。
 *
 * <pre>
 * 字段按节点类型选用：
 *   TOOL  : toolName + arguments（参数值可为 ${var} 字面量）
 *   LLM   : promptTemplate（含 ${var} 占位符）+ systemPrompt（可选）
 *   HUMAN : approverRole（可选，审批角色标识）+ rejectNext
 *   START/END : 仅作边界标记
 * </pre>
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class NodeDefinition {

    /** 节点ID（流程内唯一，如 "retrieve" / "review"） */
    private String id;

    /** 节点名称（中文，审计/展示用） */
    private String name;

    /** 节点类型 */
    private NodeType type;

    /** 节点说明 */
    private String description;

    /** TOOL：工具名（对应 ToolRegistry 注册名，如 knowledge_search） */
    private String toolName;

    /** TOOL：工具入参（key=参数名，value=字面量或 ${var} 引用） */
    private Map<String, Object> arguments = new LinkedHashMap<>();

    /** LLM：用户 prompt 模板（支持 ${var} 插值） */
    private String promptTemplate;

    /** LLM：系统 prompt（可选，约束模型角色） */
    private String systemPrompt;

    /** 产物输出 key（写入上下文供下游引用；END 节点产物作为流程结果） */
    private String outputKey;

    /** 失败重试上限（不含首次；0=不重试，失败即终止） */
    private int maxRetries = 0;

    /** 下一节点ID（null=列表下一节点；末节点 null=结束） */
    private String next;

    /** HUMAN 驳回时跳转节点ID（null=驳回即终止任务为 CANCELED） */
    private String rejectNext;

    /** HUMAN：审批角色标识（可选，用于权限校验扩展） */
    private String approverRole;
}
