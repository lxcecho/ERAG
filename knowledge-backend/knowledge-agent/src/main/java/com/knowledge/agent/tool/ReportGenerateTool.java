package com.knowledge.agent.tool;

import com.knowledge.agent.engine.AgentLlmCaller;
import com.knowledge.agent.engine.LlmIdentity;
import com.knowledge.agent.engine.LlmResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 报告生成工具：基于输入的主题与数据，调用 LLM 生成结构化报告。
 * <p>
 * 与 {@code ReportAgent} 的区别：ReportAgent 是工作流末端 Agent（消费 Analysis 产物），
 * 本工具是可复用的独立能力（任意 Agent 或外部调用方可按需调用，不依赖工作流上下文）。
 * <p>
 * 权限：{@code authRequired=false}——不接触 KB 文档数据，仅调用 LLM 生成文本，
 * 权限由调用方（Agent / Controller）在调用前保证。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerateTool implements Tool {

    private final AgentLlmCaller llmCaller;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "topic": {"type": "string", "description": "报告主题（如'2025年Q3销售政策分析'）"},
                "content": {"type": "string", "description": "报告素材/数据/分析结论（原始文本）"},
                "format": {"type": "string", "description": "输出格式: markdown(默认)/text", "default": "markdown"}
              },
              "required": ["topic", "content"]
            }""";

    private static final String SYSTEM_PROMPT = """
            你是企业知识库报告撰写者。基于提供的主题与素材，生成结构化报告。
            要求：
            - 结构清晰：标题、摘要、正文（分点）、结论
            - 关键结论后标注来源（如 [来源:文件名]）
            - 区分"资料证实"与"合理推断"
            - 使用 Markdown 格式（除非指定 text 格式）""";

    @Override
    public String name() {
        return "report_generate";
    }

    @Override
    public String description() {
        return "基于主题与素材生成结构化报告（标题/摘要/正文/结论）。"
                + "适用于将分析结论整理为正式报告、生成汇报材料等场景。";
    }

    @Override
    public String parametersJsonSchema() {
        return SCHEMA;
    }

    @Override
    public boolean authRequired() {
        return false;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String topic = (String) arguments.get("topic");
        String content = (String) arguments.get("content");
        String format = arguments.containsKey("format") && arguments.get("format") != null
                ? (String) arguments.get("format")
                : "markdown";

        String userPrompt = "报告主题：" + topic
                + "\n输出格式：" + format
                + "\n\n报告素材：\n" + content
                + "\n\n请生成报告。";

        log.info("[ReportGen] task={} 主题={} 素材长度={}", ctx.getTaskId(), topic, content.length());

        LlmResult llm = llmCaller.call(SYSTEM_PROMPT, userPrompt, "report_generate",
                LlmIdentity.of(ctx.getUserId(), ctx.getTenantId()));
        String report = llm.text();
        int tokens = llm.totalTokens();

        if (report == null || report.isBlank()) {
            return ToolResult.failure("报告生成结果为空");
        }

        log.info("[ReportGen] task={} 完成 报告长度={} token={}", ctx.getTaskId(), report.length(), tokens);
        return ToolResult.success(report, tokens);
    }
}
