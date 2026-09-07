package com.knowledge.agent.engine;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.dto.Evidence;
import com.knowledge.agent.dto.Plan;
import com.knowledge.agent.entity.AgentTask;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 执行上下文：贯穿一次工作流，承载任务信息 + 共享产物 + 配置。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>无状态 Agent 通过 Context 的 artifacts Map 传递产物（plan→evidences→analysis→report）；</li>
 *   <li>产物以 key 索引，类型由写入方保证、读取方按约定强转（内部类型安全）；</li>
 *   <li>租户/用户/KB 身份显式携带，供 {@code KnowledgeSearchTool} 做权限后过滤；</li>
 *   <li>sessionId 在任务源自聊天时非空，供 {@code PlannerAgent} 注入长期记忆上下文。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
public class AgentContext {

    private final Long taskId;
    private final Long tenantId;
    private final Long userId;
    private final Long kbId;
    private final Long sessionId;
    private final String goal;
    private final AgentProperties props;

    /** 共享产物：key=ArtifactType 名称，value=结构化对象 */
    private final Map<String, Object> artifacts = new LinkedHashMap<>();

    /** token 累计消耗（LLM Agent 上报，Executor 据此做预算校验）。线程安全：DAG 内无并发，保险起见用原子量 */
    private final AtomicInteger tokensUsed = new AtomicInteger(0);

    public AgentContext(AgentTask task, AgentProperties props) {
        this.taskId = task.getId();
        this.tenantId = task.getTenantId();
        this.userId = task.getUserId();
        this.kbId = task.getKbId();
        this.sessionId = task.getSessionId();
        this.goal = task.getGoal();
        this.props = props;
    }

    /** 写入产物（供下游 Agent 引用） */
    public void putArtifact(String key, Object value) {
        artifacts.put(key, value);
    }

    /**
     * 读取产物（按约定强转）。
     *
     * @param key 产物 key
     * @param <T>  期望类型（由调用方保证一致）
     * @return 产物对象，不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getArtifact(String key) {
        return (T) artifacts.get(key);
    }

    public boolean hasArtifact(String key) {
        return artifacts.containsKey(key);
    }

    /* ==================== 类型安全访问方法（编译期类型检查，避免运行时强转） ==================== */

    /** 获取检索计划（Planner 产物） */
    public Plan getPlan() {
        return getArtifact(ArtifactType.PLAN.name());
    }

    /** 写入检索计划 */
    public void putPlan(Plan plan) {
        putArtifact(ArtifactType.PLAN.name(), plan);
    }

    /** 获取证据列表（Knowledge 产物） */
    @SuppressWarnings("unchecked")
    public List<Evidence> getEvidences() {
        return (List<Evidence>) artifacts.get(ArtifactType.EVIDENCES.name());
    }

    /** 写入证据列表 */
    public void putEvidences(List<Evidence> evidences) {
        putArtifact(ArtifactType.EVIDENCES.name(), evidences);
    }

    /** 获取分析结论（Analysis 产物） */
    public String getAnalysis() {
        return getArtifact(ArtifactType.ANALYSIS.name());
    }

    /** 写入分析结论 */
    public void putAnalysis(String analysis) {
        putArtifact(ArtifactType.ANALYSIS.name(), analysis);
    }

    /** 获取最终报告（Report 产物） */
    public String getReport() {
        return getArtifact(ArtifactType.REPORT.name());
    }

    /** 写入最终报告 */
    public void putReport(String report) {
        putArtifact(ArtifactType.REPORT.name(), report);
    }

    /** 累加 LLM 调用 token 消耗（供 AnalysisAgent/ReportAgent 上报） */
    public void addTokens(int tokens) {
        if (tokens > 0) {
            tokensUsed.addAndGet(tokens);
        }
    }

    /** 当前累计 token 消耗 */
    public int getTokensUsed() {
        return tokensUsed.get();
    }
}
