package cn.lx.worldcoffee.message.service;

import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.message.dao.NotificationDao;
import cn.lx.worldcoffee.message.domain.Notification;
import cn.lx.worldcoffee.message.domain.NotificationEvent;
import cn.lx.worldcoffee.message.domain.vo.NotificationVO;
import cn.lx.worldcoffee.message.feign.UserFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationDao notificationDao;
    private final RabbitTemplate rabbitTemplate;
    private final UserFeignClient userFeignClient;

    public void send(NotificationEvent event) {
        // 1. 落库
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

        // 2. 发送到 RabbitMQ
        String routingKey = "notification." + event.getType().toLowerCase();
        rabbitTemplate.convertAndSend(
                "notification.exchange",
                routingKey,
                event.getReceiverId() + ":" + event.getType()
        );
    }

    public List<NotificationVO> listNotifications(Long userId, boolean unreadOnly, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getReceiverId, userId);
        if (unreadOnly) wrapper.eq(Notification::getIsRead, 0);
        wrapper.orderByDesc(Notification::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);

        List<Notification> list = notificationDao.selectList(wrapper);
        if (list.isEmpty()) return List.of();

        // 批量查询发送者信息
        List<Long> senderIds = list.stream()
                .map(Notification::getSenderId)
                .distinct()
                .collect(Collectors.toList());
        
        Map<Long, UserFeignClient.UserInfo> userMap = userFeignClient.batchGetUsers(senderIds);

        return list.stream().map(n -> {
            UserFeignClient.UserInfo sender = userMap.get(n.getSenderId());
            return NotificationVO.builder()
                    .id(n.getId())
                    .senderName(sender != null ? sender.username() : "未知")
                    .avatar(sender != null ? sender.avatar() : null)
                    .senderId(n.getSenderId())
                    .type(n.getType())
                    .content(n.getContent())
                    .postId(n.getPostId())
                    .isRead(n.getIsRead() == 1)
                    .createTime(n.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    public long countUnread(Long userId) {
        return notificationDao.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getReceiverId, userId)
                        .eq(Notification::getIsRead, 0)
        );
    }

    public void markAsRead(Long notificationId) {
        notificationDao.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .set(Notification::getIsRead, 1));
    }

    public void markAllAsRead(Long userId) {
        notificationDao.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    public void deleteNotification(Long notificationId) {
        notificationDao.deleteById(notificationId);
    }
}
