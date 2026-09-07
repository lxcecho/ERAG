package com.knowledge.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 检索计划（PlannerAgent 产出，KnowledgeAgent 消费）。
 * <p>通过 Spring AI 结构化输出（{@code .entity(Plan.class)}）由 LLM 生成。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class Plan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务理解：复述并拆解用户目标 */
    private String understanding;

    /** 检索查询列表：KnowledgeAgent 逐条调用 KnowledgeSearchTool */
    private List<String> searchQueries;

    /** 分析方向：指导 AnalysisAgent 的推理重点（如"按时间维度对比变化"） */
    private String analysisApproach;
}
