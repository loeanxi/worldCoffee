package cn.lx.worldcoffee.message.consumer;

import cn.lx.worldcoffee.message.component.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationReceiver {

    private final SseEmitterManager sseEmitterManager;

    @RabbitListener(queues = "notification.queue")
    public void handleNotification(String message) {
        // message格式: "userId:type"
        String[] parts = message.split(":");
        if (parts.length == 2) {
            String userId = parts[0];
            sseEmitterManager.sendNotification(userId, message);
        }
    }

    @RabbitListener(queues = "chat.queue.default")
    public void handleChatMessage(String message) {
        // message格式: "fromId|||content"，需要解析出 toId
        // 这里简化处理，实际应该从消息体解析完整信息
        sseEmitterManager.sendNotification("broadcast", message);
    }
}
