package cn.lx.worldcoffee.module.message.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.message.domain.vo.MessageVO;
import cn.lx.worldcoffee.module.message.domain.vo.SessionVO;
import cn.lx.worldcoffee.module.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "私信模块", description = "用户间一对一私信")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

//    	发消息，body 传 {toId: 5, content: "你好"}
    @Operation(summary = "发送私信", description = "给指定用户发送一条消息")
    @PostMapping
    public Result<MessageVO> sendMessage(@RequestBody Map<String, Object> body) {
        Long toId = Long.valueOf(body.get("toId").toString());
        String content = (String) body.get("content");
        Integer messageType = body.containsKey("messageType")
                ? Integer.valueOf(body.get("messageType").toString())
                : 1;
        return Result.success(messageService.sendMessage(toId, content, messageType));
    }

    //GET /api/messages/sessions	会话列表，类似微信聊天列表
    @Operation(summary = "会话列表", description = "当前用户的所有会话，按最后消息时间倒序")
    @GetMapping("/sessions")
    public Result<List<SessionVO>> listSessions() {
        return Result.success(messageService.listSessions());
    }

//    GET /api/messages/chat/{userId}?page=1	跟某个人的聊天记录
    @Operation(summary = "聊天记录", description = "跟某个用户的聊天历史，分页")
    @GetMapping("/chat/{userId}")
    public Result<List<MessageVO>> getChatHistory(
        @PathVariable Long userId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
    return Result.success(messageService.getChatHistory(userId, page, size));
    }

    //PUT /api/messages/chat/{userId}/read	点开聊天窗口时，把未读标为已读
    @Operation(summary = "标记已读", description = "把跟某个用户的未读消息全部标为已读")
    @PutMapping("/chat/{userId}/read")
    public Result<Void> markAsRead(@PathVariable Long userId) {
        messageService.markAsRead(userId);
        return Result.success(null);
    }

    //GET /api/messages/unread-count	底部导航栏那个未读红点数字
    @Operation(summary = "未读总数", description = "当前用户所有未读消息的总数")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        return Result.success(messageService.getUnreadCount());
    }



}
