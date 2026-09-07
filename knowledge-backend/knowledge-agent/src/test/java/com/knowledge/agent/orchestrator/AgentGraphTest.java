/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.orchestrator;

import com.knowledge.agent.engine.AgentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentGraph} 单元测试：校验图构建、next 路由（顺序+跳转）、validate 合法性检测。
 */
class AgentGraphTest {

    @Test
    void should_resolve_sequential_next_when_no_explicit_next() {
        AgentNode a = node("planner", AgentType.PLANNER);
        AgentNode b = node("knowledge", AgentType.KNOWLEDGE);
        AgentNode c = node("report", AgentType.REPORT);
        AgentGraph g = new AgentGraph("default", List.of(a, b, c));

        assertEquals(a, g.startNode());
        assertEquals(b, g.nextOf(a));
        assertEquals(c, g.nextOf(b));
        assertNull(g.nextOf(c), "末节点 nextOf 应返回 null（=END）");
    }

    @Test
    void should_resolve_explicit_next_jump_for_branching() {
        // planner -> knowledge(跳过 analysis) -> report
        AgentNode a = node("planner", AgentType.PLANNER);
        AgentNode b = node("knowledge", AgentType.KNOWLEDGE);
        b.setNext("report");
        AgentNode c = node("analysis", AgentType.ANALYSIS);
        AgentNode d = node("report", AgentType.REPORT);
        AgentGraph g = new AgentGraph("branch", List.of(a, b, c, d));

        assertEquals(b, g.nextOf(a), "a 无显式 next，取列表后继 b");
        assertEquals(d, g.nextOf(b), "b 显式 next=report，跳过 analysis");
        assertEquals(d, g.nextOf(c), "c 无显式 next，取列表后继 d");
    }

    @Test
    void should_use_explicit_start_node_id() {
        AgentNode a = node("planner", AgentType.PLANNER);
        AgentNode b = node("knowledge", AgentType.KNOWLEDGE);
        AgentGraph g = new AgentGraph("default", List.of(a, b));
        g.setStartNodeId("knowledge");

        assertEquals(b, g.startNode(), "startNodeId 显式指定应优先于列表首节点");
    }

    @Test
    void should_index_nodes_by_id() {
        AgentNode a = node("planner", AgentType.PLANNER);
        AgentNode b = node("report", AgentType.REPORT);
        AgentGraph g = new AgentGraph("default", List.of(a, b));

        assertEquals(a, g.indexById().get("planner"));
        assertEquals(b, g.indexById().get("report"));
    }

    @Test
    void should_validate_ok_for_legal_graph() {
        AgentGraph g = new AgentGraph("default", List.of(
                node("planner", AgentType.PLANNER),
                node("knowledge", AgentType.KNOWLEDGE),
                node("report", AgentType.REPORT)));
        assertDoesNotThrow(g::validate);
    }

    @Test
    void should_reject_empty_graph() {
        AgentGraph g = new AgentGraph("empty", List.of());
        IllegalStateException ex = assertThrows(IllegalStateException.class, g::validate);
        assertTrue(ex.getMessage().contains("为空"));
    }

    @Test
    void should_reject_duplicate_node_id() {
        AgentGraph g = new AgentGraph("dup", List.of(
                node("planner", AgentType.PLANNER),
                node("planner", AgentType.KNOWLEDGE)));
        IllegalStateException ex = assertThrows(IllegalStateException.class, g::validate);
        assertTrue(ex.getMessage().contains("nodeId 重复"));
    }

    @Test
    void should_reject_duplicate_agent_type() {
        AgentGraph g = new AgentGraph("dup-type", List.of(
                node("a", AgentType.PLANNER),
                node("b", AgentType.PLANNER)));
        IllegalStateException ex = assertThrows(IllegalStateException.class, g::validate);
        assertTrue(ex.getMessage().contains("agentType 重复"));
    }

    @Test
    void should_reject_unknown_start_node_id() {
        AgentGraph g = new AgentGraph("bad-start", List.of(node("planner", AgentType.PLANNER)));
        g.setStartNodeId("nonexistent");
        IllegalStateException ex = assertThrows(IllegalStateException.class, g::validate);
        assertTrue(ex.getMessage().contains("startNodeId 不存在"));
    }

    @Test
    void should_reject_cycle_in_next_chain() {
        // planner.next = report, report.next = planner（环）
        AgentNode a = node("planner", AgentType.PLANNER);
        a.setNext("report");
        AgentNode b = node("report", AgentType.REPORT);
        b.setNext("planner");
        AgentGraph g = new AgentGraph("cycle", List.of(a, b));

        IllegalStateException ex = assertThrows(IllegalStateException.class, g::validate);
        assertTrue(ex.getMessage().contains("环"));
    }

    @Test
    void should_build_graph_via_builder() {
        AgentGraph g = AgentGraph.builder()
                .code("default")
                .node(node("planner", AgentType.PLANNER))
                .node(node("report", AgentType.REPORT))
                .startNodeId("planner")
                .build();

        assertNotNull(g.startNode());
        assertEquals(2, g.getNodes().size());
        assertDoesNotThrow(g::validate);
    }

    private static AgentNode node(String id, AgentType type) {
        return AgentNode.builder()
                .nodeId(id)
                .agentType(type)
                .name(id)
                .maxRetries(0)
                .retryBackoffMs(0L)
                .timeoutMs(0L)
                .build();
    }
}
