/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool;

import com.knowledge.agent.config.AgentProperties;
import com.knowledge.agent.tool.email.EmailRequest;
import com.knowledge.agent.tool.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * {@link EmailTool} 单元测试：验证参数校验、发送委托与异常处理。
 */
@ExtendWith(MockitoExtension.class)
class EmailToolTest {

    @Mock
    private EmailSender emailSender;

    @Test
    void should_fail_when_to_empty() {
        EmailTool tool = newTool();
        ToolResult result = tool.execute(newContext(), Map.of("to", List.of(), "subject", "s", "content", "c"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("收件人"));
    }

    @Test
    void should_fail_when_email_format_invalid() {
        EmailTool tool = newTool();
        ToolResult result = tool.execute(newContext(),
                Map.of("to", List.of("not-an-email"), "subject", "s", "content", "c"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("格式非法"));
    }

    @Test
    void should_fail_when_subject_blank() {
        EmailTool tool = newTool();
        ToolResult result = tool.execute(newContext(),
                Map.of("to", List.of("a@b.com"), "subject", "  ", "content", "c"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("主题"));
    }

    @Test
    void should_fail_when_content_blank() {
        EmailTool tool = newTool();
        ToolResult result = tool.execute(newContext(),
                Map.of("to", List.of("a@b.com"), "subject", "s", "content", ""));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("正文"));
    }

    @Test
    void should_delegate_to_sender_on_valid_input() {
        EmailTool tool = newTool();
        ToolResult result = tool.execute(newContext(), Map.of(
                "to", List.of("a@b.com", "c@d.com"),
                "cc", List.of("e@f.com"),
                "subject", "周报",
                "content", "正文内容"));

        assertTrue(result.isSuccess());
        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailSender).send(captor.capture());
        EmailRequest req = captor.getValue();
        assertEquals(List.of("a@b.com", "c@d.com"), req.getTo());
        assertEquals(List.of("e@f.com"), req.getCc());
        assertEquals("周报", req.getSubject());
        assertEquals("正文内容", req.getContent());
        assertEquals("no-reply@knowledge.ai", req.getFrom());
    }

    @Test
    void should_return_failure_when_sender_throws() {
        doThrow(new IllegalStateException("SMTP 未配置")).when(emailSender).send(org.mockito.ArgumentMatchers.any());

        EmailTool tool = newTool();
        ToolResult result = tool.execute(newContext(),
                Map.of("to", List.of("a@b.com"), "subject", "s", "content", "c"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("邮件发送失败"));
    }

    @Test
    void should_not_require_auth() {
        EmailTool tool = newTool();
        assertFalse(tool.authRequired(), "邮件工具为副作用工具，由 tool_permission 角色级授权控制");
    }

    @Test
    void should_have_expected_tool_name() {
        EmailTool tool = newTool();
        assertEquals("email_send", tool.name());
    }

    // ==================== 测试辅助 ====================

    private EmailTool newTool() {
        AgentProperties props = new AgentProperties();
        // from 默认值即 no-reply@knowledge.ai，无需显式设置
        return new EmailTool(emailSender, props);
    }

    private static ToolContext newContext() {
        return ToolContext.builder()
                .tenantId(1L).userId(10L).kbId(7L).taskId(100L).stepId(null).build();
    }
}
