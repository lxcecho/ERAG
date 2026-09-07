package com.knowledge.agent.workflow.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.workflow.entity.WorkflowTask;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Workflow 执行上下文：贯穿一次流程执行，承载身份信息 + 节点间共享变量 + 执行计数。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>变量 Map：节点产物以 key 索引（outputKey→产物），下游节点通过 ${var} 引用；</li>
 *   <li>状态保存：变量可序列化为 JSON 持久化到 {@code workflow_task.context_json}，
 *       进程重启后从断点恢复（反序列化重建上下文）；</li>
 *   <li>身份显式携带：供 {@code ToolNodeHandler} 构建 {@code ToolContext} 做权限校验。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Getter
public class WorkflowContext {

    private final Long taskId;
    private final Long tenantId;
    private final Long userId;
    private final Long kbId;
    private final Long definitionId;
    private final String goal;

    /** 节点间共享变量（outputKey → 产物） */
    private final Map<String, Object> variables = new LinkedHashMap<>();

    /** 节点执行序号计数器（从1开始递增，用于 workflow_node_run.run_index） */
    private final AtomicInteger runIndex = new AtomicInteger(0);

    /** token 累计消耗（LLM 节点上报） */
    private final AtomicInteger tokensUsed = new AtomicInteger(0);

    public WorkflowContext(WorkflowTask task) {
        this.taskId = task.getId();
        this.tenantId = task.getTenantId();
        this.userId = task.getUserId();
        this.kbId = task.getKbId();
        this.definitionId = task.getDefinitionId();
        this.goal = task.getGoal();
    }

    /** 写入节点产物（供下游引用） */
    public void setVariable(String key, Object value) {
        if (key != null) {
            variables.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) variables.get(key);
    }

    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }

    /** 分配下一个执行序号 */
    public int nextRunIndex() {
        return runIndex.incrementAndGet();
    }

    public void addTokens(int tokens) {
        if (tokens > 0) {
            tokensUsed.addAndGet(tokens);
        }
    }

    public int getTokensUsed() {
        return tokensUsed.get();
    }

    // ==================== 序列化（状态保存） ====================

    /** 序列化变量为 JSON（持久化到 context_json） */
    public String serializeVariables(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception e) {
            log.warn("[Workflow] 上下文序列化失败 task={}: {}", taskId, e.getMessage());
            return "{}";
        }
    }

    /** 从 context_json 恢复变量（断点恢复） */
    public void deserializeVariables(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            Map<String, Object> loaded = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            variables.putAll(loaded);
        } catch (Exception e) {
            log.warn("[Workflow] 上下文反序列化失败 task={}，以空变量启动: {}", taskId, e.getMessage());
        }
    }
}
