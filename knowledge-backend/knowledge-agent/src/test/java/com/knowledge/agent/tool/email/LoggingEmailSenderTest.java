/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool.email;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link LoggingEmailSender} 单元测试：验证 log 模式以结构化日志代替发送，不抛异常。
 */
class LoggingEmailSenderTest {

    @Test
    void should_log_without_throwing() {
        LoggingEmailSender sender = new LoggingEmailSender();
        EmailRequest request = EmailRequest.builder()
                .from("no-reply@knowledge.ai")
                .to(List.of("a@b.com"))
                .cc(List.of("c@d.com"))
                .subject("周报")
                .content("正文")
                .taskId(100L)
                .build();

        assertDoesNotThrow(() -> sender.send(request));
    }

    @Test
    void should_handle_null_cc_gracefully() {
        LoggingEmailSender sender = new LoggingEmailSender();
        EmailRequest request = EmailRequest.builder()
                .from("no-reply@knowledge.ai")
                .to(List.of("a@b.com"))
                .cc(null)
                .subject("通知")
                .content("正文")
                .build();

        assertDoesNotThrow(() -> sender.send(request));
    }
}
