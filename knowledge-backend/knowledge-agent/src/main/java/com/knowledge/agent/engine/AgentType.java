package com.knowledge.agent.engine;

import lombok.Getter;

/**
 * Agent 角色类型（对应工作流中的 4 个专职 Agent）。
 * <p>执行顺序固定：PLANNER → KNOWLEDGE → ANALYSIS → REPORT，由 {@code @Order} 保证。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
public enum AgentType {

    PLANNER("规划者", "任务拆解：理解目标，生成结构化检索计划"),
    KNOWLEDGE("检索者", "知识检索：按计划查询并召回证据（复用 RAG + 文档权限）"),
    ANALYSIS("分析者", "分析推理：基于证据对比、归纳、推理"),
    REPORT("撰写者", "报告生成：结构化输出，结论带来源引用");

    private final String roleName;
    private final String description;

    AgentType(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }
}
