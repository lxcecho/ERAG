/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.workflow.definition;

import com.knowledge.agent.workflow.enums.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link WorkflowDefinitionModel} 路由逻辑测试：默认顺序、显式 next 跳转、rejectNext 场景。
 */
class WorkflowDefinitionModelTest {

    @Test
    void should_find_start_node() {
        WorkflowDefinitionModel m = build(NodeType.START, NodeType.TOOL, NodeType.END);
        assertEquals(NodeType.START, m.startNode().getType());
    }

    @Test
    void nextOf_should_default_to_list_successor() {
        NodeDefinition start = node("start", NodeType.START);
        NodeDefinition tool = node("retrieve", NodeType.TOOL);
        NodeDefinition end = node("end", NodeType.END);
        WorkflowDefinitionModel m = new WorkflowDefinitionModel();
        m.setNodes(List.of(start, tool, end));

        assertEquals(tool, m.nextOf(start));
        assertEquals(end, m.nextOf(tool));
        assertNull(m.nextOf(end), "末节点后继为 null");
    }

    @Test
    void nextOf_should_honor_explicit_next_jump() {
        NodeDefinition start = node("start", NodeType.START);
        NodeDefinition human = node("review", NodeType.HUMAN);
        // 显式跳转：跳过中间节点
        NodeDefinition skip = node("skip", NodeType.TOOL);
        NodeDefinition end = node("end", NodeType.END);
        start.setNext("review");
        human.setNext("end"); // 审批通过直达 end
        WorkflowDefinitionModel m = new WorkflowDefinitionModel();
        m.setNodes(List.of(start, human, skip, end));

        assertEquals(human, m.nextOf(start));
        assertEquals(end, m.nextOf(human), "显式 next 优先于列表顺序");
    }

    @Test
    void indexById_should_map_all_nodes() {
        WorkflowDefinitionModel m = build(NodeType.START, NodeType.TOOL, NodeType.HUMAN, NodeType.END);
        assertEquals(4, m.indexById().size());
    }

    private WorkflowDefinitionModel build(NodeType... types) {
        WorkflowDefinitionModel m = new WorkflowDefinitionModel();
        m.setCode("test");
        java.util.List<NodeDefinition> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            nodes.add(node("n" + i, types[i]));
        }
        m.setNodes(nodes);
        return m;
    }

    private NodeDefinition node(String id, NodeType type) {
        NodeDefinition n = new NodeDefinition();
        n.setId(id);
        n.setName(id);
        n.setType(type);
        return n;
    }
}
