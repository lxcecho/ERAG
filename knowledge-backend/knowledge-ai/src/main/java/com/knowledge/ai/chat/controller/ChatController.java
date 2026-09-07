package com.knowledge.ai.chat.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.annotation.BusinessType;
import com.knowledge.common.annotation.OperLog;
import com.knowledge.common.result.Result;
import com.knowledge.ai.chat.dto.ChatMessageVo;
import com.knowledge.ai.chat.dto.ChatSessionCreateRequest;
import com.knowledge.ai.chat.dto.ChatSessionVo;
import com.knowledge.ai.chat.service.ChatMessageService;
import com.knowledge.ai.chat.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 聊天会话与消息管理接口（RAG 问答接口见 RagController）
 *
 * @author: lxcechoo@gmail.com
 */
@Tag(name = "聊天记录接口")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    @Operation(summary = "会话分页列表")
    @GetMapping("/sessions")
    public Result<IPage<ChatSessionVo>> pageSessions(@RequestParam(required = false) Long kbId,
                                                     @RequestParam(defaultValue = "1") Integer pageNo,
                                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(chatSessionService.page(kbId, SecurityUtils.currentUserId(), pageNo, pageSize));
    }

    @Operation(summary = "新建会话")
    @PostMapping("/sessions")
    @OperLog(title = "聊天会话", businessType = BusinessType.INSERT)
    public Result<Long> createSession(@RequestBody @Valid ChatSessionCreateRequest request) {
        return Result.success(chatSessionService.create(request.getKbId(),
                SecurityUtils.currentUserId(), request.getTitle()));
    }

    @Operation(summary = "重命名会话")
    @PutMapping("/sessions/{id}")
    @OperLog(title = "聊天会话", businessType = BusinessType.UPDATE)
    public Result<Void> renameSession(@PathVariable Long id, @RequestParam String title) {
        chatSessionService.rename(id, title, SecurityUtils.currentUserId());
        return Result.success();
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{id}")
    @OperLog(title = "聊天会话", businessType = BusinessType.DELETE)
    public Result<Void> removeSession(@PathVariable Long id) {
        chatSessionService.remove(id, SecurityUtils.currentUserId());
        return Result.success();
    }

    @Operation(summary = "会话消息列表（按时间正序）")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessageVo>> listMessages(@PathVariable Long id) {
        return Result.success(chatMessageService.listBySession(id));
    }
}
