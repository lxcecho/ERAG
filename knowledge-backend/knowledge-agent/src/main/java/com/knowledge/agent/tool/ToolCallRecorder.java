package com.knowledge.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.agent.entity.AgentMessage;
import com.knowledge.agent.mapper.AgentMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具调用记录器：将每次工具调用的完整 IO 持久化到 agent_message 表，供审计与回放。
 * <p>
 * 记录内容（序列化为 JSON 存入 {@code agent_message.content}）：
 * <ul>
 *   <li>tool：工具名</li>
 *   <li>input：入参 JSON</li>
 *   <li>output：输出摘要（截断防超大字段）</li>
 *   <li>success：是否成功</li>
 *   <li>durationMs：执行耗时</li>
 *   <li>error：失败原因（成功时为 null）</li>
 * </ul>
 * <p>
 * 审计要求（项目硬约束）：所有工具调用必须可追溯——关联 tenantId / taskId / stepId，
 * 便于安全审计与问题定位。taskId 为 null 时（直接调用，非 Agent 工作流）仍记录，仅 task_id 为空。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCallRecorder {

    private final AgentMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    /** content 字段最大长度（截断防 LONGTEXT 超限影响查询性能） */
    private static final int MAX_CONTENT_LENGTH = 65535;

    /**
     * 记录一次工具调用（执行后调用）。
     *
     * @param ctx        执行上下文
     * @param toolName   工具名
     * @param input      入参
     * @param result     执行结果
     * @param durationMs 执行耗时（毫秒）
     */
    public void record(ToolContext ctx, String toolName,
                       Map<String, Object> input, ToolResult result, long durationMs) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("tool", toolName);
            record.put("input", input);
            record.put("success", result.isSuccess());
            record.put("durationMs", durationMs);

            if (result.isSuccess()) {
                record.put("output", truncate(objectMapper.writeValueAsString(result.getData())));
            } else {
                record.put("error", result.getErrorMessage());
            }

            AgentMessage msg = new AgentMessage();
            msg.setTenantId(ctx.getTenantId());
            msg.setTaskId(ctx.getTaskId());
            msg.setStepId(ctx.getStepId());
            msg.setRole("tool");
            msg.setToolName(toolName);
            msg.setContent(truncate(objectMapper.writeValueAsString(record)));
            msg.setTokenUsage(result.getTokensUsed());
            msg.setCreateTime(LocalDateTime.now());
            messageMapper.insert(msg);
        } catch (Exception e) {
            // 审计记录失败不应阻断主流程，降级为日志
            log.warn("[ToolRecorder] 工具调用记录失败 tool={}: {}", toolName, e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null || s.length() <= MAX_CONTENT_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_CONTENT_LENGTH);
    }
}
