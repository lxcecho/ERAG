package com.knowledge.agent.orchestrator;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 编排图（顺序 + 分支路由模型）。
 * <p>
 * 镜像 {@code WorkflowDefinitionModel} 的 next 解析模式：节点列表为默认顺序，{@link AgentNode#getNext()}
 * 显式指向可实现跳转分支。执行期由 {@code AgentExecutor} 按图遍历，每节点带重试/超时/回滚。
 * <p>
 * 不可变模板：一份图对应一组有序节点。默认图由 {@code AgentScheduler} 从注册的 Agent 列表自动构建。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class AgentGraph {

    /** 图编码（审计/日志用） */
    private String code;

    /** 节点列表（顺序即默认执行顺序） */
    private List<AgentNode> nodes = new ArrayList<>();

    /** 起始节点ID（null=列表首节点） */
    private String startNodeId;

    public AgentGraph() {
    }

    public AgentGraph(String code, List<AgentNode> nodes) {
        this.code = code;
        this.nodes = nodes;
    }

    /** 构建 id → 节点 索引（执行期 O(1) 查找，含 next 跳转解析） */
    public Map<String, AgentNode> indexById() {
        Map<String, AgentNode> idx = new LinkedHashMap<>();
        if (nodes != null) {
            for (AgentNode n : nodes) {
                idx.put(n.getNodeId(), n);
            }
        }
        return idx;
    }

    /** 起始节点：startNodeId 指向或列表首节点 */
    public AgentNode startNode() {
        if (startNodeId != null && !startNodeId.isBlank()) {
            AgentNode n = indexById().get(startNodeId);
            if (n != null) {
                return n;
            }
        }
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        return nodes.get(0);
    }

    /**
     * 解析下一节点：优先用 next 显式指定，否则取列表后继；末节点返回 null（=END）。
     *
     * @param current 当前节点
     * @return 下一节点，无则 null（流程结束）
     */
    public AgentNode nextOf(AgentNode current) {
        if (current == null) {
            return null;
        }
        if (current.getNext() != null && !current.getNext().isBlank()) {
            return indexById().get(current.getNext());
        }
        int idx = nodes.indexOf(current);
        if (idx < 0 || idx + 1 >= nodes.size()) {
            return null;
        }
        return nodes.get(idx + 1);
    }

    /**
     * 校验图合法性：
     * <ul>
     *   <li>非空且每个节点有 nodeId/agentType；</li>
     *   <li>nodeId 唯一、agentType 唯一（同图不重复执行同角色）；</li>
     *   <li>startNodeId 存在；</li>
     *   <li>next 链无环（从起点遍历去重检测）。</li>
     * </ul>
     *
     * @throws IllegalStateException 图非法时
     */
    public void validate() {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException("编排图节点为空: " + code);
        }
        Set<String> nodeIds = new HashSet<>();
        Set<com.knowledge.agent.engine.AgentType> types = new HashSet<>();
        for (AgentNode n : nodes) {
            if (n.getNodeId() == null || n.getNodeId().isBlank()) {
                throw new IllegalStateException("编排图存在空 nodeId: " + code);
            }
            if (!nodeIds.add(n.getNodeId())) {
                throw new IllegalStateException("编排图 nodeId 重复: " + n.getNodeId());
            }
            if (n.getAgentType() == null) {
                throw new IllegalStateException("节点 agentType 为空: " + n.getNodeId());
            }
            if (!types.add(n.getAgentType())) {
                throw new IllegalStateException("编排图 agentType 重复: " + n.getAgentType() + " (node=" + n.getNodeId() + ")");
            }
        }
        if (startNodeId != null && !startNodeId.isBlank() && !nodeIds.contains(startNodeId)) {
            throw new IllegalStateException("startNodeId 不存在: " + startNodeId);
        }
        // next 链无环检测：从起点沿 nextOf 遍历，遇已访问即环
        AgentNode cur = startNode();
        Set<String> visited = new HashSet<>();
        while (cur != null) {
            if (!visited.add(cur.getNodeId())) {
                throw new IllegalStateException("编排图存在环（next 链回路）: " + cur.getNodeId());
            }
            cur = nextOf(cur);
        }
    }

    /** 链式构建器入口 */
    public static AgentGraphBuilder builder() {
        return new AgentGraphBuilder();
    }

    /** 简易链式构建器（避免 Lombok @Builder 与手写混合冲突） */
    public static class AgentGraphBuilder {
        private String code;
        private final List<AgentNode> nodes = new ArrayList<>();
        private String startNodeId;

        public AgentGraphBuilder code(String code) {
            this.code = code;
            return this;
        }

        public AgentGraphBuilder node(AgentNode node) {
            this.nodes.add(node);
            return this;
        }

        public AgentGraphBuilder nodes(List<AgentNode> nodes) {
            this.nodes.clear();
            this.nodes.addAll(nodes);
            return this;
        }

        public AgentGraphBuilder startNodeId(String startNodeId) {
            this.startNodeId = startNodeId;
            return this;
        }

        public AgentGraph build() {
            AgentGraph g = new AgentGraph(code, nodes);
            g.setStartNodeId(startNodeId);
            return g;
        }
    }
}
