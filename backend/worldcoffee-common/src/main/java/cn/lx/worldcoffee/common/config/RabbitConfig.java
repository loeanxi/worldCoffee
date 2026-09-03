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
        return new Queue(NOTIFICATION_QUEUE, true); // true=持久化
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_ROUTING_KEY);
    }

    // ─── 私信交换机 + 队列 ──────────────────────────

    //CHAT_ROUTING_KEY_PREFIX 拆开就是：
    //
    //单词	意思
    //CHAT	聊天的
    //ROUTING	路由（邮递员送信时看的地址）
    //KEY	钥匙/标识
    //PREFIX	前缀（开头的部分）
    //合起来："聊天消息的路由地址的前缀"。
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

    // ─── 秒杀订单交换机 + 队列 + 死信队列 ────────────
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    public static final String SECKILL_ORDER_DEAD_EXCHANGE = "seckill.order.dead.exchange";
    public static final String SECKILL_ORDER_DEAD_QUEUE = "seckill.order.dead.queue";
    public static final String SECKILL_ORDER_DEAD_ROUTING_KEY = "seckill.order.dead";



    /** 秒杀订单死信交换机 */
    @Bean
    public TopicExchange seckillOrderDeadExchange() {
        return new TopicExchange(SECKILL_ORDER_DEAD_EXCHANGE);
    }

    /** 秒杀订单死信队列 */
    @Bean
    public Queue seckillOrderDeadQueue() {
        return new Queue(SECKILL_ORDER_DEAD_QUEUE, true);
    }
    /** 秒杀订单死信队列绑定 */
    @Bean
    public Binding seckillOrderDeadBinding() {
        return BindingBuilder.bind(seckillOrderDeadQueue())
                .to(seckillOrderDeadExchange())
                .with(SECKILL_ORDER_DEAD_ROUTING_KEY);
    }


    /** 秒杀订单交换机 */
    @Bean
    public TopicExchange seckillOrderExchange() {
        return new TopicExchange(SECKILL_ORDER_EXCHANGE);
    }
    /** 秒杀订单普通队列（带死信交换机参数） */
    @Bean
    public Queue seckillOrderQueue() {
        Map<String, Object> args = new HashMap<>();
        // 消息被拒绝或 TTL 过期后，转发到死信交换机
        args.put("x-dead-letter-exchange", SECKILL_ORDER_DEAD_EXCHANGE);
        // 转发时用的 routingKey，死信交换机按这个 key 路由到死信队列
        args.put("x-dead-letter-routing-key", SECKILL_ORDER_DEAD_ROUTING_KEY);
        //QueueBuilder.durable(name) 等价于 new Queue(name, true)，但更灵活，适合加参数。
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE).withArguments(args).build();
    }
    /** 秒杀订单普通队列绑定 */
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
        //// 消息过期后，转发到同一个交换机
        args.put("x-dead-letter-exchange", ORDER_TIMEOUT_EXCHANGE);
        //// 转发时用的 routingKey
        args.put("x-dead-letter-routing-key", ORDER_TIMEOUT_ROUTING_KEY);
        //// 消息存活时间 15 分钟
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

/**
 *  关于消息队列构造方法方法签名：
 * public Queue(String name, boolean durable)
 * name：队列名称
 * durable 是什么意思
 * true：队列是持久化的，RabbitMQ 重启后队列还在
 * false：队列是临时的，RabbitMQ 重启后消失
 * new Queue(SECKILL_ORDER_DEAD_QUEUE, true);
 * 即创建一个持久化的死信队列
 *
 * // 只指定名字，默认非持久、不排他、不自动删除
 * new Queue(String name);
 *
 * // 持久化
 * new Queue(String name, boolean durable);
 *
 * // 持久化 + 排他 + 自动删除
 * new Queue(String name, boolean durable, boolean exclusive, boolean autoDelete);
 *exclusive 就是这个队列只能由创建它的那个连接使用，别的连接用不了，连接断开队列就自动删掉。
 * RabbitMQ 里一个客户端连接创建队列时，如果标记了 exclusive=true，意思就是这个队列是专属于这个连接的。
 * 别人用不了，自己连接断了也没了。
 * 举个例子
 * 假设用户 A 连接 RabbitMQ，创建了一个 exclusive 队列：
 * new Queue("a.private.queue", true, true, false, null);
 * 只有用户 A 的这个连接能往这个队列发消息、从这个队列收消息
 * 用户 B 想监听这个队列？不行
 * 用户 A 的程序关闭，连接断开，这个队列自动删除
 *典型场景：临时队列、回调队列、一对一通信
 * 比如 RPC 调用时，每个客户端临时创建一个队列收响应：
 * String replyQueue = channel.queueDeclare().getQueue();
 * // 这个队列默认就是 exclusive 的
 * 或者某些工具自动生成的唯一队列名，只给当前应用实例自己用。
 *
 *
 * // 完整版，带参数
 * new Queue(String name, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments);
 *
 * durable	持久化
 * exclusive	是否排他，只允许当前连接访问
 * autoDelete	最后一个消费者断开时是否自动删除
 * arguments	额外参数，比如 x-dead-letter-exchange、x-message-ttl 等
 *
 *
 *
 */
