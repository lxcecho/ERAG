package com.knowledge.agent.tool;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.tool.email.EmailRequest;
import com.knowledge.agent.tool.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件发送工具：副作用工具，按 log/smtp 两种模式发送。
 * <p>
 * <b>副作用工具授权</b>：authRequired=false（不依赖 KB 权限），由 tool_permission 表的角色级授权
 * 控制谁能调用（默认开放，需显式收紧时插入 R 类型拒绝记录）。
 * <p>
 * <b>发送模式</b>：{@code agent.tool.email.mode=log}（默认，日志代替发送）/ {@code smtp}（真实发送），
 * 由 {@link EmailSender} 实现按配置装配，EmailTool 不感知具体实现。
 * <p>
 * <b>参数校验</b>：to 非空且为合法邮箱列表、subject/content 非空；非法参数返回失败结果。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailTool implements Tool {

    private final EmailSender emailSender;
    private final AgentProperties props;

    /** 简易邮箱正则（够用校验，不追求 RFC 完备） */
    private static final String EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "to": {"type": "array", "items": {"type": "string"}, "description": "收件人邮箱列表"},
                "subject": {"type": "string", "description": "邮件主题"},
                "content": {"type": "string", "description": "邮件正文"},
                "cc": {"type": "array", "items": {"type": "string"}, "description": "抄送邮箱列表"}
              },
              "required": ["to", "subject", "content"]
            }""";

    @Override
    public String name() {
        return "email_send";
    }

    @Override
    public String description() {
        return "发送邮件(支持 log/smtp 两种模式)，副作用工具需授权。"
                + "to 为收件人邮箱列表，subject 为主题，content 为正文。";
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
        // 1. 解析并校验收件人
        List<String> to = toStringList(arguments.get("to"));
        if (to.isEmpty()) {
            return ToolResult.failure("收件人(to)不能为空");
        }
        List<String> invalid = to.stream().filter(e -> !e.matches(EMAIL_REGEX)).toList();
        if (!invalid.isEmpty()) {
            return ToolResult.failure("收件人邮箱格式非法: " + invalid);
        }

        // 2. 校验主题与正文
        String subject = arguments.get("subject") == null ? null : String.valueOf(arguments.get("subject")).trim();
        if (subject == null || subject.isEmpty()) {
            return ToolResult.failure("邮件主题(subject)不能为空");
        }
        String content = arguments.get("content") == null ? null : String.valueOf(arguments.get("content"));
        if (content == null || content.isEmpty()) {
            return ToolResult.failure("邮件正文(content)不能为空");
        }

        // 3. 抄送（可空，需校验格式）
        List<String> cc = toStringList(arguments.get("cc"));
        if (!cc.isEmpty()) {
            List<String> invalidCc = cc.stream().filter(e -> !e.matches(EMAIL_REGEX)).toList();
            if (!invalidCc.isEmpty()) {
                return ToolResult.failure("抄送邮箱格式非法: " + invalidCc);
            }
        }

        // 4. 构造请求并委托发送
        String from = props.getTool().getEmail().getFrom();
        EmailRequest request = EmailRequest.builder()
                .from(from)
                .to(to)
                .cc(cc.isEmpty() ? null : cc)
                .subject(subject)
                .content(content)
                .taskId(ctx.getTaskId())
                .build();

        try {
            emailSender.send(request);
        } catch (Exception e) {
            log.warn("[EmailSend] 发送失败 task={}: {}", ctx.getTaskId(), e.getMessage());
            return ToolResult.failure("邮件发送失败: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", from);
        data.put("to", to);
        data.put("cc", cc);
        data.put("subject", subject);
        data.put("contentLength", content.length());
        log.info("[EmailSend] task={} to={} subject={} 发送完成", ctx.getTaskId(), to, subject);
        return ToolResult.success(data);
    }

    /** 将入参（可能是 List<String> 或 List<Object>）统一转为 List<String> */
    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null && !String.valueOf(o).trim().isEmpty()) {
                    result.add(String.valueOf(o).trim());
                }
            }
            return result;
        }
        return List.of();
    }
}
