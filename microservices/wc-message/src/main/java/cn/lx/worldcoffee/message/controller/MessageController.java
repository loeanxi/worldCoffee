package cn.lx.worldcoffee.message.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.message.domain.from.SendMessageForm;
import cn.lx.worldcoffee.message.domain.vo.MessageVO;
import cn.lx.worldcoffee.message.domain.vo.SessionVO;
import cn.lx.worldcoffee.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "私信模块", description = "用户间一对一私信")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "发送私信", description = "给指定用户发送一条消息")
    @PostMapping
    public Result<MessageVO> sendMessage(@Valid @RequestBody SendMessageForm form) {
        return Result.success(messageService.sendMessage(form));
    }

    @Operation(summary = "会话列表", description = "当前用户的所有会话，按最后消息时间倒序")
    @GetMapping("/sessions")
    public Result<List<SessionVO>> listSessions() {
        return Result.success(messageService.listSessions());
    }

    @Operation(summary = "聊天记录", description = "跟某个用户的聊天历史，分页")
    @GetMapping("/chat/{userId}")
    public Result<List<MessageVO>> getChatHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(messageService.getChatHistory(userId, page, size));
    }

    @Operation(summary = "标记已读", description = "把跟某个用户的未读消息全部标为已读")
    @PutMapping("/chat/{userId}/read")
    public Result<Void> markAsRead(@PathVariable Long userId) {
        messageService.markAsRead(userId);
        return Result.success(null);
    }

    @Operation(summary = "未读总数", description = "当前用户所有未读消息的总数")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        return Result.success(messageService.getUnreadCount());
    }
}
