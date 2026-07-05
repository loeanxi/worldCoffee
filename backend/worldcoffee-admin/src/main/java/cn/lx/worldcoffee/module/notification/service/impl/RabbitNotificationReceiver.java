package cn.lx.worldcoffee.module.notification.service.impl;

import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.common.redis.NotificationMessageReceiver;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor   //消费者
public class RabbitNotificationReceiver {
    private final NotificationMessageReceiver sseReceiver;

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void handleNotification(String message) {
        // message 格式: "3:LIKE"（userId:type）
        String[] parts = message.split(":");
        if (parts.length < 2) return;

        String userId = parts[0];
        String type = parts[1];

        // 调用现有的 SSE 推送逻辑
        sseReceiver.sendNotification(userId, type);
    }
}
