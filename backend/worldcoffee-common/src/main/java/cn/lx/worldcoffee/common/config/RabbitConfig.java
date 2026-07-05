package cn.lx.worldcoffee.common.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // ─── 通知交换机 + 队列 ──────────────────────────
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.#";

    //CHAT_ROUTING_KEY_PREFIX 拆开就是：
    //
    //单词	意思
    //CHAT	聊天的
    //ROUTING	路由（邮递员送信时看的地址）
    //KEY	钥匙/标识
    //PREFIX	前缀（开头的部分）
    //合起来："聊天消息的路由地址的前缀"。
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE, true); // true=持久化
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_ROUTING_KEY);
    }

    // ─── 私信交换机 + 队列 ──────────────────────────

    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String CHAT_QUEUE_PREFIX = "chat.queue.";
    public static final String CHAT_ROUTING_KEY_PREFIX = "chat.";

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(CHAT_EXCHANGE);
    }

    /**
     * 创建私信队列：每个用户一个专属队列
     * chat.queue.5  = 用户5的收件箱
     * chat.queue.3  = 用户3的收件箱
     */
    @Bean
    public Queue chatQueue() {
        return new Queue(CHAT_QUEUE_PREFIX + "default", true);
    }

    @Bean
    public Binding chatBinding() {
        return BindingBuilder.bind(chatQueue())
                .to(chatExchange())
                .with(CHAT_ROUTING_KEY_PREFIX + "*");
    }
}