package com.knowledge.agent.engine;

/**
 * Agent 产物类型（跨步骤引用的结构化产物）。
 *
 * @author: lxcechoo@gmail.com
 */
public enum ArtifactType {

    /** Planner 产出的检索计划 */
    PLAN,
    /** Knowledge 检索召回的证据集合 */
    EVIDENCES,
    /** Analysis 产出的分析结论 */
    ANALYSIS,
    /** Report 产出的最终报告 */
    REPORT
}
