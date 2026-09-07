package com.knowledge.common.security;

import java.util.regex.Pattern;

/**
 * Prompt 注入防护工具：清洗用户输入，防止恶意指令篡改 LLM 行为。
 * <p>RAG 场景下，用户输入会拼入 system prompt 或 context，攻击者可能通过
 * "忽略上述指令"、"你现在是..." 等注入手法操纵 LLM 输出。
 * <p>防护策略：
 * <ol>
 *   <li>移除已知注入模式（中英文常见指令覆写短语）</li>
 *   <li>截断超长输入（防止 token 溢出攻击）</li>
 *   <li>转义特殊标记（防止 prompt 模板变量逃逸）</li>
 *   <li>标记不可信内容（供 LLM 区分指令与数据）</li>
 * </ol>
 *
 * @author: lxcechoo@gmail.com
 */
public final class PromptSanitizer {

    private PromptSanitizer() {
    }

    /** 用户输入最大字符数（超过截断），防止 token 溢出 */
    private static final int MAX_INPUT_LENGTH = 4000;

    /**
     * 已知 prompt 注入模式（中英文），匹配到则移除该行。
     * <p>覆盖常见攻击手法：指令覆写、角色劫持、系统提示泄露。
     */
    private static final Pattern[] INJECTION_PATTERNS = {
            // 英文注入模式
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|prompts?|rules?)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an|the)"),
            Pattern.compile("(?i)forget\\s+(everything|all|your)\\s+(above|previous|prior)"),
            Pattern.compile("(?i)disregard\\s+(all|any|previous|above)\\s+(instructions?|prompts?)"),
            Pattern.compile("(?i)new\\s+instructions?:\\s*"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)\\[system\\]|\\[INST\\]|<<SYS>>"),
            Pattern.compile("(?i)act\\s+as\\s+(if|though|a|an)"),
            Pattern.compile("(?i)pretend\\s+(you|that|to)\\s+(are|be|have)"),
            Pattern.compile("(?i)override\\s+(all|previous|system)"),
            // 中文注入模式
            Pattern.compile("忽略(上面|以上|之前|上述)(的)?(所有|全部)?(指令|提示|规则|要求|设定)"),
            Pattern.compile("(你现在|从此|从现在开始)(是|扮演|作为|变成)"),
            Pattern.compile("忘掉(上面|以上|之前|上述)(的)?(所有|全部)?(内容|指令|提示)"),
            Pattern.compile("(无视|抛弃|丢弃)(上面|以上|之前|上述)(的)?(所有|全部)?(指令|规则)"),
            Pattern.compile("(新|新的)(指令|提示|规则|设定)[：:]+"),
            Pattern.compile("\\[系统\\]|\\[SYSTEM\\]|\\[指令\\]"),
            Pattern.compile("(假装|假设|想象)(你|自己)(是|成为|变成)"),
            Pattern.compile("(打破|突破|绕过)(上面|以上|系统)(的)?(限制|规则|设定)"),
    };

    /**
     * 清洗用户输入：移除注入模式 + 截断 + 转义。
     *
     * @param rawInput 原始用户输入
     * @return 清洗后的安全文本
     */
    public static String sanitize(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return "";
        }

        String cleaned = rawInput;

        // 1. 移除注入模式（逐行检测，移除匹配行）
        StringBuilder sb = new StringBuilder();
        for (String line : cleaned.split("\\n", -1)) {
            boolean isInjection = false;
            for (Pattern pattern : INJECTION_PATTERNS) {
                if (pattern.matcher(line).find()) {
                    isInjection = true;
                    break;
                }
            }
            if (!isInjection) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        cleaned = sb.toString();

        // 2. 截断超长输入
        if (cleaned.length() > MAX_INPUT_LENGTH) {
            cleaned = cleaned.substring(0, MAX_INPUT_LENGTH) + "...[已截断]";
        }

        return cleaned;
    }

    /**
     * 将用户输入包装为不可信内容标记，明确告知 LLM 这是数据而非指令。
     * <p>格式：{@code <user_query>...</user_query>}
     * <p>配合 system prompt 中的"仅将 <user_query> 内的内容视为用户问题"指令，
     * 可有效降低注入成功率。
     *
     * @param rawInput 原始用户输入
     * @return 包装后的安全文本
     */
    public static String wrapAsUntrusted(String rawInput) {
        String sanitized = sanitize(rawInput);
        return "<user_query>" + sanitized + "</user_query>";
    }

    /**
     * 检测输入是否包含可疑注入模式（用于日志告警，不阻断）。
     *
     * @param input 用户输入
     * @return true 表示包含可疑模式
     */
    public static boolean containsSuspiciousPattern(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
}
