package com.knowledge.agent.tool.email;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 邮件发送请求（EmailTool 与 EmailSender 之间的契约）。
 * <p>由 EmailTool 校验非空后构造，交由 {@link EmailSender} 实现按模式（log/smtp）发送。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
@Builder
public class EmailRequest {

    /** 发件人地址（取自 agent.tool.email.from） */
    private final String from;

    /** 收件人邮箱列表（至少一个） */
    private final List<String> to;

    /** 抄送邮箱列表（可空） */
    private final List<String> cc;

    /** 邮件主题 */
    private final String subject;

    /** 邮件正文（纯文本） */
    private final String content;

    /** 关联任务ID（审计用，可空） */
    private final Long taskId;
}
