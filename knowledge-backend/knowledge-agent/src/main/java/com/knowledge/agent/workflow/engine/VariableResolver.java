package com.knowledge.agent.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量解析器：把节点配置中的 ${var} 占位符替换为上下文变量值。
 * <p>
 * 两类解析：
 * <ul>
 *   <li>{@link #resolveString}：模板插值，${var} 替换为变量的字符串形式（LLM prompt 用）；</li>
 *   <li>{@link #resolveArgs}：工具入参解析——若值整体为单个 ${var}，保留原对象类型（如 List/Map/Number），
 *       否则做字符串插值（TOOL 参数用，避免把结构化产物拍平为 toString）。</li>
 * </ul>
 * 变量缺失时替换为空串（容错，不中断流程），并交由调用方按需校验。
 *
 * @author: lxcechoo@gmail.com
 */
@Component
@RequiredArgsConstructor
public class VariableResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectMapper objectMapper;

    /** 模板插值：${var} → 变量字符串（LLM prompt 模板用） */
    public String resolveString(String template, WorkflowContext ctx) {
        if (template == null || !template.contains("${")) {
            return template;
        }
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).trim();
            Object val = ctx.getVariable(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(toText(val)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 工具入参解析：整体 ${var} 保留原类型，否则字符串插值。
     *
     * @param args 原始入参（value 可为字面量或 ${var}）
     * @return 解析后入参
     */
    public Map<String, Object> resolveArgs(Map<String, Object> args, WorkflowContext ctx) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (args == null) {
            return resolved;
        }
        for (Map.Entry<String, Object> e : args.entrySet()) {
            resolved.put(e.getKey(), resolveValue(e.getValue(), ctx));
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value, WorkflowContext ctx) {
        if (!(value instanceof String s)) {
            return value; // 非字符串原样返回
        }
        Matcher single = Pattern.compile("^\\$\\{([^}]+)}$").matcher(s);
        if (single.matches()) {
            // 整体引用：保留原对象类型
            return ctx.getVariable(single.group(1).trim());
        }
        return resolveString(s, ctx);
    }

    /** 变量转文本：null→空串；String→本身；其他→JSON（便于 LLM 消费结构化产物） */
    private String toText(Object val) {
        if (val == null) {
            return "";
        }
        if (val instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(val);
        } catch (Exception e) {
            return String.valueOf(val);
        }
    }
}
