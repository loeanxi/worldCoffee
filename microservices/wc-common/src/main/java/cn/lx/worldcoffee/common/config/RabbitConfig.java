package cn.lx.worldcoffee.common.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    // ─── 通知交换机 + 队列 ──────────────────────────
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.#";


    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE, true);
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

    // ─── 秒杀订单交换机 + 队列 + 死信队列 ────────────
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    public static final String SECKILL_ORDER_DEAD_EXCHANGE = "seckill.order.dead.exchange";
    public static final String SECKILL_ORDER_DEAD_QUEUE = "seckill.order.dead.queue";
    public static final String SECKILL_ORDER_DEAD_ROUTING_KEY = "seckill.order.dead";

    @Bean
    public TopicExchange seckillOrderDeadExchange() {
        return new TopicExchange(SECKILL_ORDER_DEAD_EXCHANGE);
    }

    @Bean
    public Queue seckillOrderDeadQueue() {
        return new Queue(SECKILL_ORDER_DEAD_QUEUE, true);
    }

    @Bean
    public Binding seckillOrderDeadBinding() {
        return BindingBuilder.bind(seckillOrderDeadQueue())
                .to(seckillOrderDeadExchange())
                .with(SECKILL_ORDER_DEAD_ROUTING_KEY);
    }

    @Bean
    public TopicExchange seckillOrderExchange() {
        return new TopicExchange(SECKILL_ORDER_EXCHANGE);
    }

    @Bean
    public Queue seckillOrderQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", SECKILL_ORDER_DEAD_EXCHANGE);
        args.put("x-dead-letter-routing-key", SECKILL_ORDER_DEAD_ROUTING_KEY);
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE).withArguments(args).build();
    }

    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    // ─── 订单超时延时队列 ──────────────────────────
    public static final String ORDER_TIMEOUT_EXCHANGE = "order.timeout.exchange";
    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "order.timeout.delay.queue";
    public static final String ORDER_TIMEOUT_QUEUE = "order.timeout.queue";
    public static final String ORDER_TIMEOUT_DELAY_ROUTING_KEY = "order.timeout.delay";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout";

    @Bean
    public TopicExchange orderTimeoutExchange() {
        return new TopicExchange(ORDER_TIMEOUT_EXCHANGE);
    }

    @Bean
    public Queue orderTimeoutDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", ORDER_TIMEOUT_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_TIMEOUT_ROUTING_KEY);
        args.put("x-message-ttl", 15 * 60 * 1000);  // 15 分钟
        return QueueBuilder.durable(ORDER_TIMEOUT_DELAY_QUEUE).withArguments(args).build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return new Queue(ORDER_TIMEOUT_QUEUE, true);
    }

    @Bean
    public Binding orderTimeoutDelayBinding() {
        return BindingBuilder.bind(orderTimeoutDelayQueue())
                .to(orderTimeoutExchange())
                .with(ORDER_TIMEOUT_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderTimeoutExchange())
                .with(ORDER_TIMEOUT_ROUTING_KEY);
    }

}
