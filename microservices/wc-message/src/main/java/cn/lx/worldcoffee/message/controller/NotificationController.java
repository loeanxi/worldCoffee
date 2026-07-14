package cn.lx.worldcoffee.message.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.message.component.SseEmitterManager;
import cn.lx.worldcoffee.message.domain.vo.NotificationVO;
import cn.lx.worldcoffee.message.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "通知模块", description = "SSE 实时推送、通知列表、已读标记")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitterManager sseEmitterManager;
    private final NotificationService notificationService;

    @Operation(summary = "SSE 订阅", description = "建立长连接，服务端实时推送新通知给前端")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        Long userId = SecurityUtils.requireUserId();
        SseEmitter emitter = new SseEmitter(0L);
        sseEmitterManager.addEmitter(userId.toString(), emitter);
        emitter.onCompletion(() -> sseEmitterManager.removeEmitter(userId.toString(), emitter));
        emitter.onTimeout(() -> sseEmitterManager.removeEmitter(userId.toString(), emitter));
        return emitter;
    }

    @Operation(summary = "通知列表", description = "分页获取通知，filter=unread 只看未读，filter=all 看全部")
    @GetMapping
    public Result<List<NotificationVO>> list(
            @Parameter(description = "unread / all") @RequestParam(defaultValue = "all") String filter,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.requireUserId();
        boolean unreadOnly = "unread".equals(filter);
        return Result.success(notificationService.listNotifications(userId, unreadOnly, page, size));
    }

    @Operation(summary = "未读数量", description = "返回当前用户未读通知数，用于前端红点 badge")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = SecurityUtils.requireUserId();
        return Result.success(notificationService.countUnread(userId));
    }

    @Operation(summary = "标记单条已读", description = "用户点击某条通知时调用，将 is_read 改为 1")
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@Parameter(description = "通知ID") @PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success(null);
    }

    @Operation(summary = "一键全部已读", description = "将当前用户所有未读通知批量标记为已读")
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        Long userId = SecurityUtils.requireUserId();
        notificationService.markAllAsRead(userId);
        return Result.success(null);
    }

    @Operation(summary = "删除通知", description = "用户删除自己不想看的单条通知")
    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(@Parameter(description = "通知ID") @PathVariable Long id) {
        notificationService.deleteNotification(id);
        return Result.success(null);
    }
}
