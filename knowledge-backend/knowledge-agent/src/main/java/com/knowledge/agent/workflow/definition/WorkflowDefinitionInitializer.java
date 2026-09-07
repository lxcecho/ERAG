package com.knowledge.agent.workflow.definition;

import com.knowledge.agent.workflow.config.WorkflowProperties;
import com.knowledge.agent.workflow.engine.WorkflowDefinitionService;
import com.knowledge.agent.workflow.entity.WorkflowDefinition;
import com.knowledge.agent.workflow.enums.NodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预置流程初始化器：启动时幂等注册"政策分析"流程（检索→比较→总结→人工审核→报告生成）。
 * <p>注册到系统租户（tenantId=0），所有租户可通过 code=policy_analysis 引用全局模板。
 * <p>已存在同 code+version 则跳过，保证幂等。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class WorkflowDefinitionInitializer implements ApplicationRunner {

    private final WorkflowDefinitionService definitionService;
    private final WorkflowProperties props;

    public static final String POLICY_ANALYSIS_CODE = "policy_analysis";

    @Override
    public void run(ApplicationArguments args) {
        registerPolicyAnalysis();
    }

    /** 政策分析流程：检索 → 比较 → 总结 → 人工审核 → 报告生成 */
    private void registerPolicyAnalysis() {
        long sysTenant = props.getSystemTenantId();
        if (definitionService.exists(sysTenant, POLICY_ANALYSIS_CODE, 1)) {
            log.info("[Workflow] 预置流程 {} 已存在，跳过注册", POLICY_ANALYSIS_CODE);
            return;
        }

        WorkflowDefinitionModel model = new WorkflowDefinitionModel();
        model.setCode(POLICY_ANALYSIS_CODE);
        model.setName("政策分析流程");
        model.setNodes(List.of(
                startNode(),
                retrieveNode(),
                compareNode(),
                summarizeNode(),
                reviewNode(),
                reportNode(),
                endNode()
        ));

        WorkflowDefinition def = new WorkflowDefinition();
        def.setTenantId(sysTenant);
        def.setCode(POLICY_ANALYSIS_CODE);
        def.setName(model.getName());
        def.setVersion(1);
        def.setStatus("ENABLED");
        def.setDefinition(definitionService.toJson(model));
        def.setDescription("政策分析可控流程：检索证据→对比分析→归纳总结→人工审核→生成报告");
        definitionService.save(def);
        log.info("[Workflow] 预置流程 {} v1 注册完成（共{}节点）", POLICY_ANALYSIS_CODE, model.getNodes().size());
    }

    private NodeDefinition startNode() {
        NodeDefinition n = new NodeDefinition();
        n.setId("start");
        n.setName("开始");
        n.setType(NodeType.START);
        n.setDescription("流程入口");
        return n;
    }

    /** 检索：调 knowledge_search 工具，产物 evidences */
    private NodeDefinition retrieveNode() {
        NodeDefinition n = new NodeDefinition();
        n.setId("retrieve");
        n.setName("检索证据");
        n.setType(NodeType.TOOL);
        n.setToolName("knowledge_search");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "${goal}");
        args.put("topK", 8);
        n.setArguments(args);
        n.setOutputKey("evidences");
        n.setMaxRetries(1);
        n.setDescription("从知识库检索与目标相关的证据片段");
        return n;
    }

    /** 比较：LLM 对比证据，产物 comparison */
    private NodeDefinition compareNode() {
        NodeDefinition n = new NodeDefinition();
        n.setId("compare");
        n.setName("对比分析");
        n.setType(NodeType.LLM);
        n.setSystemPrompt("你是政策分析专家，擅长对比多份资料的异同与变化。");
        n.setPromptTemplate("""
                基于以下检索到的证据，对比分析其异同、变化与冲突点：
                目标：${goal}
                证据：
                ${evidences}

                请输出结构化对比：相同点 / 差异点 / 变化趋势。""");
        n.setOutputKey("comparison");
        n.setMaxRetries(1);
        n.setDescription("LLM 对检索证据做对比分析");
        return n;
    }

    /** 总结：LLM 归纳，产物 summary */
    private NodeDefinition summarizeNode() {
        NodeDefinition n = new NodeDefinition();
        n.setId("summarize");
        n.setName("归纳总结");
        n.setType(NodeType.LLM);
        n.setSystemPrompt("你是政策分析专家，请客观归纳，区分'资料证实'与'合理推断'。");
        n.setPromptTemplate("""
                基于以下对比分析，归纳核心结论：
                目标：${goal}
                对比分析：
                ${comparison}

                请输出：核心结论 / 关键发现 / 风险提示。""");
        n.setOutputKey("summary");
        n.setMaxRetries(1);
        n.setDescription("LLM 归纳总结核心结论");
        return n;
    }

    /** 人工审核：暂停等待审批，产物 reviewComment */
    private NodeDefinition reviewNode() {
        NodeDefinition n = new NodeDefinition();
        n.setId("review");
        n.setName("人工审核");
        n.setType(NodeType.HUMAN);
        n.setOutputKey("reviewComment");
        n.setApproverRole("reviewer");
        n.setDescription("人工审核总结结论，通过后生成正式报告");
        return n;
    }

    /** 报告生成：调 report_generate 工具，产物 report */
    private NodeDefinition reportNode() {
        NodeDefinition n = new NodeDefinition();
        n.setId("report");
        n.setName("生成报告");
        n.setType(NodeType.TOOL);
        n.setToolName("report_generate");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "${goal}");
        args.put("content", "${summary}");
        args.put("format", "markdown");
        n.setArguments(args);
        n.setOutputKey("report");
        n.setMaxRetries(0);
        n.setDescription("基于总结生成结构化正式报告");
        return n;
    }

    private NodeDefinition endNode() {
        NodeDefinition n = new NodeDefinition();
        n.setId("end");
        n.setName("结束");
        n.setType(NodeType.END);
        n.setOutputKey("report");
        n.setDescription("流程出口，report 变量作为最终结果");
        return n;
    }
}
