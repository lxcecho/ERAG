package com.knowledge.agent.workflow.definition;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程定义模型（workflow_definition.definition JSON 反序列化产物）。
 * <p>不可变模板：一份 code+version 对应一组有序节点。执行时由 {@code WorkflowExecutor} 按序遍历。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class WorkflowDefinitionModel {

    /** 流程编码（与 workflow_definition.code 对应） */
    private String code;

    /** 流程名称 */
    private String name;

    /** 节点列表（顺序即默认执行顺序；START 必须为首，END 必须为尾） */
    private List<NodeDefinition> nodes = new ArrayList<>();

    /**
     * 构建 id → 节点 索引（执行期 O(1) 查找，含 next 跳转解析）。
     *
     * @return 不可变索引 Map
     */
    public Map<String, NodeDefinition> indexById() {
        Map<String, NodeDefinition> idx = new LinkedHashMap<>();
        for (NodeDefinition n : nodes) {
            idx.put(n.getId(), n);
        }
        return idx;
    }

    /** 起始节点（START 类型） */
    public NodeDefinition startNode() {
        return nodes.stream()
                .filter(n -> n.getType() == com.knowledge.agent.workflow.enums.NodeType.START)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("流程定义缺少 START 节点: " + code));
    }

    /**
     * 解析下一节点：优先用 next 显式指定，否则取列表后继；末节点返回 null。
     *
     * @param current 当前节点
     * @return 下一节点，无则 null（流程结束）
     */
    public NodeDefinition nextOf(NodeDefinition current) {
        if (current == null) {
            return null;
        }
        // 显式 next 优先
        if (current.getNext() != null && !current.getNext().isBlank()) {
            return indexById().get(current.getNext());
        }
        // 默认：列表后继
        int idx = nodes.indexOf(current);
        if (idx < 0 || idx + 1 >= nodes.size()) {
            return null;
        }
        return nodes.get(idx + 1);
    }
}
