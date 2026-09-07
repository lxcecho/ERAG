package com.knowledge.ai.prompt;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 内置模板与渲染工具。
 * <p>设计原因：
 * <ul>
 *   <li>将提示词与代码解耦，便于调优。系统提示约束模型"只依据资料回答、不编造"，是 RAG 抑制幻觉的关键。</li>
 *   <li>统一采用 {@code {varName}} 命名占位符（而非 {@code %s} 顺序占位符），
 *       与版本化 DB 模板（prompt_template）保持一致，可由 {@link #render} 统一渲染。</li>
 * </ul>
 * <p>当 {@code ai.rag.use-db-template=false} 或未找到已发布 DB 模板时，{@link #SYSTEM_TEMPLATE} 作为兜底。
 *
 * @author: lxcechoo@gmail.com
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /**
     * 系统提示词模板（内置兜底），{@code {context}} 占位符替换为检索到的编号文本片段。
     */
    public static final String SYSTEM_TEMPLATE = """
            你是企业知识库助手。请严格依据下方【参考资料】回答用户问题。
            回答规则：
            1. 答案必须基于参考资料，不得编造或引入资料外的信息。
            2. 若参考资料不足以回答，请直接回复"知识库中暂无相关信息"。
            3. 必须标注引用来源：引用某条资料内容的句子末尾用 [编号] 标注（如 [1] 或 [1][3]），编号对应【参考资料】中的条目顺序；未引用资料时不标注。

            【参考资料】
            {context}
            """;

    /**
     * 普通对话系统提示词（内置兜底，不检索知识库）。
     * <p>用于 useRag=false 的直接大模型对话场景，约束模型专业作答、不编造。
     * 与 DB 模板 prompt_code=default_system 对应。
     */
    public static final String PLAIN_SYSTEM_TEMPLATE = """
            你是企业知识库助手，请以专业、准确、简洁的方式回答用户问题。
            当信息不足时如实告知，不编造内容。可结合历史对话上下文进行多轮交流。
            """;

    /**
     * 引用标注修复提示词（代码层强制兜底，非人工可编辑模板）。
     * <p>LLM 对"必须标注引用"规则的遵循存在概率性：同一提示词下，个别问题/轮次
     * 会漏掉 [编号] 标注。当回答不含任何引用标注时，由 {@link #buildRepairPrompt}
     * 构造本提示词触发一次二次生成，要求模型仅补引用、不改实质内容。
     * {@code {context}} 为编号参考资料上下文，{@code {draft}} 为漏标的初稿回答。
     */
    public static final String CITATION_REPAIR_TEMPLATE = """
            你是一名校对员。下面的【待修正回答】基于【参考资料】生成，但遗漏了引用标注。
            请按规则修正回答：
            1. 不得新增、删除或篡改原回答的实质内容，只补上引用标注。
            2. 引用某条资料内容的句子末尾用 [编号] 标注（如 [1] 或 [1][3]），编号对应【参考资料】中的条目顺序；未引用资料时不标注。
            3. 直接输出修正后的完整回答，不要输出任何解释、前后缀或多余内容。

            【参考资料】
            {context}

            【待修正回答】
            {draft}
            """;

    /** 引用标注正则：匹配 [1] 或 [1][3] 等连续编号 */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[\\d+\\](\\[\\d+\\])*");

    /**
     * 将检索结果拼装为编号上下文文本。
     *
     * @param texts   切片文本列表
     * @param sources 对应来源列表
     * @return 编号拼装后的上下文
     */
    public static String buildContext(List<String> texts, List<String> sources) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texts.size(); i++) {
            sb.append("[").append(i + 1).append("] (来源: ")
              .append(sources.get(i)).append(")\n")
              .append(texts.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 判断文本是否含 [编号] 引用标注（如 [1] 或 [1][3]）。
     */
    public static boolean containsCitation(String text) {
        return text != null && CITATION_PATTERN.matcher(text).find();
    }

    /**
     * 校验文本中所有引用编号是否落在 1..maxIdx 范围内。
     * <p>防止修复/生成阶段模型编造出超范围的编号（幻觉引用）。
     * 文本无任何引用时返回 true（配合 {@link #containsCitation} 使用）。
     */
    public static boolean citationsWithinRange(String text, int maxIdx) {
        if (text == null || maxIdx < 1) {
            return false;
        }
        Matcher m = CITATION_PATTERN.matcher(text);
        while (m.find()) {
            for (String part : m.group().split("\\]\\[")) {
                int n = Integer.parseInt(part.replaceAll("[\\[\\]]", ""));
                if (n < 1 || n > maxIdx) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 构造引用标注修复提示词（漏标时二次生成用）。
     *
     * @param context 编号参考资料上下文
     * @param draft   缺引用的初稿回答
     * @return 渲染后的修复提示词（作为 system message 传入 LLM）
     */
    public static String buildRepairPrompt(String context, String draft) {
        return render(CITATION_REPAIR_TEMPLATE, Map.of(
                "context", context == null ? "" : context,
                "draft", draft == null ? "" : draft));
    }

    /**
     * 命名占位符渲染：将 {@code {varName}} 替换为 variables 中对应值。
     * <p>未提供值的占位符保持原样（便于发现遗漏变量），值为 null 时替换为空串。
     *
     * @param content   模板内容（含 {var} 占位符）
     * @param variables 变量键值表
     * @return 渲染后的文本
     */
    public static String render(String content, Map<String, String> variables) {
        if (content == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return content;
        }
        String result = content;
        for (Map.Entry<String, String> e : variables.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }
}
