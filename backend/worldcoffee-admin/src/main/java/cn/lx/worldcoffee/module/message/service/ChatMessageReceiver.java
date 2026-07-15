package cn.lx.worldcoffee.module.message.service;

import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.common.redis.NotificationMessageReceiver;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessageReceiver {
    private final NotificationMessageReceiver sseReceiver;

    @RabbitListener(queues = RabbitConfig.CHAT_QUEUE_PREFIX + "default")
    public void handleChatMessage(
            //注意这里有个巧妙设计：通过 @Header("amqp_receivedRoutingKey") 拿到消息实际的路由 Key，从中截取收信人 ID。这样即使所有私信进同一个队列，也能知道该推给谁。
            String message,
            @Header("amqp_receivedRoutingKey") String routingKey
    ){
        // routingKey = "chat.5" → receiverId = "5"
        String receiverId = routingKey.substring(5);

        //// 改之前：MQ 只传消息ID，前端收到后还得再发 HTTP 请求查内容
        //String mqMessage = fromId + ":" + msg.getId();
        //
        //// 改之后：MQ 直接传完整内容，前端收到就能直接显示
        //String mqMessage = fromId + "|||" + content;
        // message格式: "3|||你好啊" → senderId="3", content="你好啊"
        String[] parts = message.split("\\|\\|\\|", 2);
        if (parts.length < 2) return;
        String senderId = parts[0];
        String content = parts[1];

        // 推 SSE 给收信人
        sseReceiver.sendNotification(receiverId, "chat:" + senderId + "|||" + content);
    }
}
