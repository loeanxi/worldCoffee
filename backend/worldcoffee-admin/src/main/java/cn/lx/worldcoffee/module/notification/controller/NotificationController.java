package cn.lx.worldcoffee.module.notification.controller;

import cn.lx.worldcoffee.common.redis.NotificationMessageReceiver;
import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.coffee.service.CoffeeService;
import cn.lx.worldcoffee.module.notification.domain.vo.NotificationVO;
import cn.lx.worldcoffee.module.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

//SSE 订阅接口
@Tag(name = "通知模块", description = "SSE 实时推送、通知列表、已读标记")
@RestController
@AllArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationMessageReceiver receiver;
    private final NotificationService notificationService;
    private final CoffeeService coffeeService;

    private Long getCurrentUserId(){
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null){
                return Long.valueOf(auth.getPrincipal().toString());
            }
        }catch (Exception ignored){}
        return null;
    }


    /**
     * 前端调用这个接口来建立长连接，服务端返回一个 SseEmitter 对象并保持连接不关闭，后续就能往这个通道里推消息。
     *
     * 几个关键点：
     *
     * produces = TEXT_EVENT_STREAM_VALUE：告诉浏览器"这个接口返回的是 SSE 流"，浏览器就会用 EventSource 来对接。
     * new SseEmitter(0L)：超时时间设为 0 表示永不超时，连接一直保持着，直到主动断开。
     * onCompletion / onTimeout：连接关闭或超时时自动把 emitter 从 sseMap 里移除，防止内存泄漏。
     *
     */
    // SSE 订阅：前端建立长连接，等推送
    @Operation(summary = "SSE 订阅", description = "建立长连接，服务端实时推送新通知给前端")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        Long uId = getCurrentUserId();
        if (uId == null) throw new ServiceException("请先登录");
        String userId = uId.toString();
        SseEmitter emitter = new SseEmitter(0L);  // 不过期
        receiver.addEmitter(userId, emitter);
        emitter.onCompletion(() -> receiver.removeEmitter(userId, emitter));
        emitter.onTimeout(() -> receiver.removeEmitter(userId, emitter));
        return emitter;
    }

    /**
     * 消息列表 —— 替代旧的 /unread
     * GET /api/notifications?filter=unread&page=1&size=20
     * filter = "unread" | "all"
     */
    //为什么列表接口路径是空的 @GetMapping 而不是 @GetMapping("/list")？
    // 因为 REST 风格里，GET /api/notifications 本身就代表"获取通知集合"，
    // /unread-count 表示这个集合的一个统计子资源。
    @Operation(summary = "通知列表", description = "分页获取通知，filter=unread 只看未读，filter=all 看全部")
    @GetMapping
    public Result<List<NotificationVO>> list(
            @Parameter(description = "unread / all") @RequestParam(defaultValue = "all") String filter,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        boolean unreadOnly = "unread".equals(filter);
        return Result.success(notificationService.listNotifications(userId, unreadOnly, page, size));
    }

    /**
     * 未读数量 —— 前端红点 badge
     * GET /api/notifications/unread-count
     */
    @Operation(summary = "未读数量", description = "返回当前用户未读通知数，用于前端红点 badge")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = getCurrentUserId();
        return Result.success(notificationService.countUnread(userId));
    }

    /**
     * markAsRead — 标记通知为已读
     *
     * 用户点开某条通知时，把 isRead 改成已读状态，
     * 对应你之前 Notification 实体里的 isRead 字段。
     *
     */
    // 标记已读
    @Operation(summary = "标记单条已读", description = "用户点击某条通知时调用，将 is_read 改为 1")
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(
            @Parameter(description = "通知ID") @PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success(null);
    }

    /**
     * 一键全部已读
     * PUT /api/notifications/read-all
     */
    @Operation(summary = "一键全部已读", description = "将当前用户所有未读通知批量标记为已读")
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        Long userId = getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return Result.success(null);
    }

    @Operation(summary = "删除通知", description = "用户删除自己不想看的单条通知")
    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(
            @Parameter(description = "通知ID") @PathVariable Long id) {
        notificationService.deleteNotification(id);
        return Result.success(null);
    }


//    旧版方法
//    @GetMapping("/unread")
//    public Result<List<NotificationVO>> getUnread() {
//        Long userId = getCurrentUserId();
//        return Result.success(notificationService.listUnread(userId));
//    }

//    @Override
//    public List<NotificationVO> listUnread(Long userId) {
//        // SQL: SELECT * FROM notification WHERE receiver_id = ? ORDER BY create_time DESC LIMIT 20
//        List<Notification> list = notificationDao.selectList(
//                new LambdaQueryWrapper<Notification>()
//                        .eq(Notification::getReceiverId, userId)
//                        .orderByDesc(Notification::getCreateTime)
//                        .last("LIMIT 20")
//        );
//
//        return list.stream().map(n -> {
//            User sender = userDao.selectById(n.getSenderId());
//            return NotificationVO.builder()
//                    .id(n.getId())
//                    .senderName(sender != null ? sender.getUsername() : "未知")
//                    .type(n.getType())
//                    .content(n.getContent())
//                    .postId(n.getPostId())
//                    .isRead(n.getIsRead() == 1)
//                    .createTime(n.getCreateTime())
//                    .build();
//        }).collect(Collectors.toList());
//    }

}
