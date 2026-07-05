package cn.lx.worldcoffee.module.notification.service.impl;

import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.module.notification.dao.NotificationDao;
import cn.lx.worldcoffee.module.notification.domain.Notification;
import cn.lx.worldcoffee.module.notification.domain.NotificationEvent;
import cn.lx.worldcoffee.module.notification.domain.vo.NotificationVO;
import cn.lx.worldcoffee.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Primary   // ← 加这行，优先使用 RabbitMQ 实现
@RequiredArgsConstructor
public class RabbitNotificationService implements NotificationService {
    private final NotificationDao notificationDao;
    private final RabbitTemplate rabbitTemplate;
    private final UserDao userDao;


    @Override
    public void send(NotificationEvent event) {
        // 1. 落库（跟之前一样）
        Notification notification = new Notification();
        notification.setReceiverId(event.getReceiverId());
        notification.setSenderId(event.getSenderId());
        notification.setType(event.getType());
        notification.setPostId(event.getPostId());
        notification.setCommentId(event.getCommentId());
        notification.setContent(event.getContent());
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        notificationDao.insert(notification);

        // 2. 用 RabbitMQ 推送（替换之前的 Redis Pub/Sub）
        // routingKey = "notification.like" / "notification.comment" 等
        String routingKey = "notification." + event.getType().toLowerCase();
        rabbitTemplate.convertAndSend(
                RabbitConfig.NOTIFICATION_EXCHANGE,
                routingKey,
                event.getReceiverId() + ":" + event.getType()
        );
    }

    // === 以下方法跟原来一模一样，直接复制过来 ===

    @Override
    public List<NotificationVO> listNotifications(Long userId, boolean unreadOnly, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getReceiverId, userId);
        if (unreadOnly) wrapper.eq(Notification::getIsRead, 0);
        wrapper.orderByDesc(Notification::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);

        List<Notification> list = notificationDao.selectList(wrapper);
        if (list.isEmpty()) return List.of();

        List<Long> senderIds = list.stream()
                .map(Notification::getSenderId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userDao.selectBatchIds(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return list.stream().map(n -> {
            User sender = userMap.get(n.getSenderId());
            return NotificationVO.builder()
                    .id(n.getId())
                    .senderName(sender != null ? sender.getUsername() : "未知")
                    .avatar(sender != null ? sender.getAvatar() : null)
                    .senderId(n.getSenderId())
                    .type(n.getType())
                    .content(n.getContent())
                    .postId(n.getPostId())
                    .isRead(n.getIsRead() == 1)
                    .createTime(n.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public long countUnread(Long userId) {
        return notificationDao.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getReceiverId, userId)
                        .eq(Notification::getIsRead, 0)
        );
    }

    @Override
    public void markAsRead(Long notificationId) {
        notificationDao.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public void markAllAsRead(Long userId) {
        notificationDao.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public void deleteNotification(Long notificationId) {
        notificationDao.deleteById(notificationId);
    }
    /**
     * 你的代码发通知
     *   │  rabbitTemplate.convertAndSend()
     *   ▼
     * notification.exchange（交换机）
     *   │  按 routingKey = "notification.like" 路由
     *   ▼
     * notification.queue（队列）
     *   │  消息存着
     *   ▼
     * @RabbitListener（消费者自动取走）
     *   │  推 SSE 给前端
     */
}
