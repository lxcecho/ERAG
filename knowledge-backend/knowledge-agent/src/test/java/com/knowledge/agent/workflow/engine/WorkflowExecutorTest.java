/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.workflow.definition.NodeDefinition;
import com.knowledge.agent.workflow.definition.WorkflowDefinitionModel;
import com.knowledge.agent.workflow.entity.WorkflowDefinition;
import com.knowledge.agent.workflow.entity.WorkflowNodeRun;
import com.knowledge.agent.workflow.entity.WorkflowTask;
import com.knowledge.agent.workflow.enums.NodeType;
import com.knowledge.agent.workflow.enums.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowExecutor} 单元测试：覆盖节点遍历、状态保存、HUMAN 暂停/恢复、失败重试。
 * <p>mock 持久层（taskManager）与定义服务，注入可控的 NodeHandler，验证编排逻辑与状态机转换。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowExecutorTest {

    @Mock
    private WorkflowDefinitionService definitionService;
    @Mock
    private WorkflowTaskManager taskManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 辅助构建 ====================

    private WorkflowExecutor buildExecutor(NodeHandler... handlers) {
        return new WorkflowExecutor(definitionService, taskManager, objectMapper, List.of(handlers));
    }

    private WorkflowTask createdTask() {
        WorkflowTask t = new WorkflowTask();
        t.setId(1L);
        t.setTenantId(1L);
        t.setUserId(10L);
        t.setKbId(100L);
        t.setDefinitionId(50L);
        t.setGoal("分析政策");
        t.setStatus(WorkflowStatus.CREATED.name());
        t.setRetryCount(0);
        return t;
    }

    private WorkflowTask waitingHumanTask(String currentNode) {
        WorkflowTask t = createdTask();
        t.setStatus(WorkflowStatus.WAITING_HUMAN.name());
        t.setCurrentNode(currentNode);
        return t;
    }

    private WorkflowDefinition def() {
        WorkflowDefinition d = new WorkflowDefinition();
        d.setId(50L);
        d.setTenantId(1L);
        d.setCode("policy_analysis");
        d.setVersion(1);
        return d;
    }

    private NodeDefinition startNode() {
        NodeDefinition n = new NodeDefinition();
        n.setId("start");
        n.setType(NodeType.START);
        n.setName("开始");
        return n;
    }

    private NodeDefinition toolNode(String id, String toolName, String outputKey, int maxRetries) {
        NodeDefinition n = new NodeDefinition();
        n.setId(id);
        n.setName(id);
        n.setType(NodeType.TOOL);
        n.setToolName(toolName);
        n.setOutputKey(outputKey);
        n.setMaxRetries(maxRetries);
        return n;
    }

    private NodeDefinition humanNode(String id) {
        NodeDefinition n = new NodeDefinition();
        n.setId(id);
        n.setName("审核");
        n.setType(NodeType.HUMAN);
        n.setOutputKey("reviewComment");
        return n;
    }

    private NodeDefinition endNode(String outputKey) {
        NodeDefinition n = new NodeDefinition();
        n.setId("end");
        n.setType(NodeType.END);
        n.setOutputKey(outputKey);
        return n;
    }

    private WorkflowDefinitionModel model(NodeDefinition... nodes) {
        WorkflowDefinitionModel m = new WorkflowDefinitionModel();
        m.setCode("policy_analysis");
        m.setName("测试流程");
        m.setNodes(List.of(nodes));
        return m;
    }

    /** 桩：getById 返回任务 */
    private void stubTask(WorkflowTask task) {
        when(taskManager.getById(1L)).thenReturn(task);
    }

    /** 桩：startNodeRun 返回带 id 的记录（仅执行节点的测试需要） */
    private void stubNodeRun() {
        when(taskManager.startNodeRun(anyLong(), anyLong(), anyString(), any(), anyString(),
                anyInt(), anyInt(), any())).thenAnswer(inv -> {
            WorkflowNodeRun r = new WorkflowNodeRun();
            r.setId(900L);
            return r;
        });
    }

    private NodeHandler mockToolHandler(NodeType type, NodeExecutionResult result) {
        NodeHandler h = mock(NodeHandler.class);
        when(h.type()).thenReturn(type);
        when(h.handle(any(), any())).thenReturn(result);
        return h;
    }

    // ==================== 测试用例 ====================

    @Test
    void start_should_complete_flow_with_tool_node() {
        // START → TOOL(report_generate, outputKey=report) → END(outputKey=report)
        WorkflowDefinitionModel m = model(startNode(),
                toolNode("gen", "report_generate", "report", 0), endNode("report"));
        when(definitionService.getById(50L)).thenReturn(def());
        when(definitionService.toModel(any())).thenReturn(m);
        stubTask(createdTask());
        stubNodeRun();

        NodeHandler toolHandler = mockToolHandler(NodeType.TOOL,
                NodeExecutionResult.success("正式报告内容", 80));
        WorkflowExecutor executor = buildExecutor(toolHandler);

        executor.start(1L);

        verify(taskManager).completeTask(1L, "正式报告内容");
        verify(taskManager, never()).failTask(anyLong(), anyString(), anyString());
    }

    @Test
    void start_should_pause_on_human_node() {
        // START → HUMAN(review) → END
        WorkflowDefinitionModel m = model(startNode(), humanNode("review"), endNode("report"));
        when(definitionService.getById(50L)).thenReturn(def());
        when(definitionService.toModel(any())).thenReturn(m);
        stubTask(createdTask());
        stubNodeRun();

        buildExecutor().start(1L); // 无 TOOL 节点，handlers 为空

        // 暂停：currentNode=review，状态 WAITING_HUMAN
        verify(taskManager).pauseForHuman(1L, "review");
        verify(taskManager, never()).completeTask(anyLong(), anyString());
        verify(taskManager, never()).failTask(anyLong(), anyString(), anyString());
    }

    @Test
    void resume_approved_should_continue_and_complete() {
        // 已暂停于 review：START → HUMAN(review) → END(outputKey=report)
        WorkflowDefinitionModel m = model(startNode(), humanNode("review"), endNode("report"));
        when(definitionService.getById(50L)).thenReturn(def());
        when(definitionService.toModel(any())).thenReturn(m);
        stubTask(waitingHumanTask("review"));

        WorkflowNodeRun waitRun = new WorkflowNodeRun();
        waitRun.setId(900L);
        when(taskManager.findWaitingHumanRun(anyLong(), eq("review"))).thenReturn(waitRun);

        buildExecutor().resume(1L, true, 10L, "同意发布");

        verify(taskManager).approveNodeRun(900L, true, 10L, "同意发布");
        verify(taskManager).completeTask(eq(1L), anyString());
    }

    @Test
    void resume_rejected_should_cancel_when_no_reject_next() {
        WorkflowDefinitionModel m = model(startNode(), humanNode("review"), endNode("report"));
        when(definitionService.getById(50L)).thenReturn(def());
        when(definitionService.toModel(any())).thenReturn(m);
        stubTask(waitingHumanTask("review"));

        WorkflowNodeRun waitRun = new WorkflowNodeRun();
        waitRun.setId(900L);
        when(taskManager.findWaitingHumanRun(anyLong(), eq("review"))).thenReturn(waitRun);

        buildExecutor().resume(1L, false, 10L, "结论不严谨，驳回");

        verify(taskManager).cancelTask(eq(1L), anyString());
        verify(taskManager, never()).completeTask(anyLong(), anyString());
    }

    @Test
    void tool_failure_with_no_retry_should_fail_task() {
        // START → TOOL(maxRetries=0) → END
        WorkflowDefinitionModel m = model(startNode(),
                toolNode("gen", "report_generate", "report", 0), endNode("report"));
        when(definitionService.getById(50L)).thenReturn(def());
        when(definitionService.toModel(any())).thenReturn(m);
        stubTask(createdTask());
        stubNodeRun();

        NodeHandler toolHandler = mockToolHandler(NodeType.TOOL,
                NodeExecutionResult.failure("LLM 调用超时"));
        buildExecutor(toolHandler).start(1L);

        verify(taskManager).failTask(eq(1L), eq("NODE_FAILED"), anyString());
        verify(taskManager, never()).completeTask(anyLong(), anyString());
    }

    @Test
    void tool_failure_should_auto_retry_then_succeed() {
        // START → TOOL(maxRetries=1) → END：首次失败，重试成功
        WorkflowDefinitionModel m = model(startNode(),
                toolNode("gen", "report_generate", "report", 1), endNode("report"));
        when(definitionService.getById(50L)).thenReturn(def());
        when(definitionService.toModel(any())).thenReturn(m);
        stubTask(createdTask());
        stubNodeRun();

        NodeHandler toolHandler = mock(NodeHandler.class);
        when(toolHandler.type()).thenReturn(NodeType.TOOL);
        when(toolHandler.handle(any(), any()))
                .thenReturn(NodeExecutionResult.failure("瞬时错误"))
                .thenReturn(NodeExecutionResult.success("重试后报告", 50));
        buildExecutor(toolHandler).start(1L);

        verify(toolHandler, times(2)).handle(any(), any());
        verify(taskManager).completeTask(1L, "重试后报告");
    }

    @Test
    void retry_should_restart_from_failed_node() {
        // FAILED 任务：listNodeRuns 返回一条 FAILED 记录，retry 从该节点重跑成功
        WorkflowDefinitionModel m = model(startNode(),
                toolNode("gen", "report_generate", "report", 0), endNode("report"));
        when(definitionService.getById(50L)).thenReturn(def());
        when(definitionService.toModel(any())).thenReturn(m);

        WorkflowTask failedTask = createdTask();
        failedTask.setStatus(WorkflowStatus.FAILED.name());
        when(taskManager.getById(1L)).thenReturn(failedTask);
        when(taskManager.startNodeRun(anyLong(), anyLong(), anyString(), any(), anyString(),
                anyInt(), anyInt(), any())).thenAnswer(inv -> {
            WorkflowNodeRun r = new WorkflowNodeRun();
            r.setId(900L);
            return r;
        });
        when(taskManager.listNodeRuns(anyLong())).thenReturn(List.of());

        NodeHandler toolHandler = mockToolHandler(NodeType.TOOL,
                NodeExecutionResult.success("重试报告", 30));
        buildExecutor(toolHandler).retry(1L);

        verify(taskManager).completeTask(1L, "重试报告");
    }

    @Test
    void cancel_should_mark_canceled() {
        when(taskManager.getById(1L)).thenReturn(createdTask());
        buildExecutor().cancel(1L, "用户取消");

        verify(taskManager).cancelTask(eq(1L), eq("用户取消"));
    }
}
