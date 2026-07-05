package cn.lx.worldcoffee.common.config;

import cn.lx.worldcoffee.common.redis.NotificationMessageReceiver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

//这个配置类做了两件事：建立一个"消息接收管理员"和一个"消息翻译官"。我从整体到细节来讲。
//这是 Redis 发布/订阅（Pub/Sub）模式的服务端接收配置。发送方往某个频道发消息，这个配置负责监听匹配的频道，收到消息后转交给业务处理类。
@Configuration
public class RedisPubSubConfig {


    @Bean
    //1. 配置监听容器 listenerContainer
    public RedisMessageListenerContainer listenerContainer(
            RedisConnectionFactory factory, MessageListenerAdapter adapter)
    {
        //RedisMessageListenerContainer 可以理解为一个"消息收件箱的管理员"
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        //连接 Redis（setConnectionFactory）
        container.setConnectionFactory(factory);
        //告诉 Redis："我要订阅所有匹配 notify:* 模式的频道"（比如 notify:like、notify:comment 都会匹配到）
        //收到消息后，交给 adapter 处理
        container.addMessageListener(adapter, new PatternTopic("notify:*"));
        return container;
    }

    @Bean
    //MessageListenerAdapter 是一个"翻译官"。Redis 传过来的消息是原始格式，adapter 负责把它转成 Java 对象，然后调用你指定的方法。
    //这里写的是 receiver, "onMessage"，意思就是收到消息后调用 NotificationMessageReceiver 这个类里的 onMessage 方法。
    public MessageListenerAdapter listenerAdapter(NotificationMessageReceiver receiver)
    {
        return new MessageListenerAdapter(receiver, "onMessage");
    }
    //listenerAdapter() 方法相当于你雇了一个翻译，告诉他"以后有人用外语跟你说话，你就翻译成中文告诉老板"。
    // 雇的时候没有翻译任何内容，等有外国人来说话了，翻译才开始工作。

    //两层通信
    /**整体架构：
     * Redis Pub/Sub（内部消息总线）          SSE（推送到前端）
     *
     * 其他服务/模块                          你的Spring Boot                          浏览器
     *    发布消息  ──────────→  Redis  ──→  接收消息  ──────────→  SSE  ──→  前端显示
     *                  ↑                    ↑                                      ↑
     *               你配的是这一段           这一段才是发到前端
     */
    //你配的这个"接收管理员"，不是在接收前端的消息，而是在接收 Redis 的消息。
    /**
     * 具体场景：用户 A 给用户 B 点了个赞。
     *
     * 第一步，点赞服务把消息发到 Redis：
     *
     *
     * redisTemplate.convertAndSend("notify:like", "用户A赞了你的文章");
     * 这一步就是"发送"，通常用 RedisTemplate.convertAndSend() 就够了，不需要额外配置发送管理员，Spring 已经帮你做好了。
     *
     * 第二步，你配的这个 listenerContainer 收到这条 Redis 消息。
     *
     * 第三步，在 onMessage 里通过 SSE 把消息推给用户 B 的浏览器：
     *
     *
     * public void onMessage(String message) {
     *     // 通过 SseEmitter 推给前端
     *     sseEmitter.send(SseEmitter.event().data(message));
     * }
     * 所以完整的链路是：Redis 发布 → 你的监听器接收 → 通过 SSE 推给前端。
     *
     * 你之所以觉得"应该配发送管理员"，是因为你把 Redis 和 SSE 混在一起看了。实际上 SSE 才是"发送给前端"的那一层，而 Redis Pub/Sub 这一层的角色是内部消息中转——你的程序在这一层确实是接收方。发送方是其他业务模块（点赞、评论、关注等），它们通过 RedisTemplate 直接发送，不需要额外配置。
     *
     * 这样做的好处是解耦：点赞模块只管往 Redis 发一条消息，不用关心通知怎么推送；通知模块只管监听 Redis 然后通过 SSE 往前端推。两边互不干扰，以后加新的通知类型也很方便。
     */


    //上面例子的具体场景演示：
    /**
     * 场景：用户 A（id=1001）给用户 B（id=2002）点了个赞
     *
     * ① 前端发起请求
     *
     * 用户 A 在前端点击"赞"按钮，浏览器发送一个普通的 HTTP 请求：
     *
     * POST /api/like { "fromUserId": 1001, "toUserId": 2002, "articleId": 88 }
     * ② 点赞 Controller 处理
     *
     *
     * @PostMapping("/like")
     * public Result like(@RequestBody LikeRequest req) {
     *     // 1. 把点赞记录写入数据库
     *     likeService.save(req);
     *
     *     // 2. 往 Redis 发一条通知消息
     *     String msg = "用户" + req.getFromUserId() + "赞了你的文章" + req.getArticleId();
     *     redisTemplate.convertAndSend("notify:like", msg);
     *
     *     return Result.ok();
     * }
     * 这一步做了两件事：写数据库、发 Redis 消息。convertAndSend 的第一个参数是频道名，第二个是消息内容。调完这行，消息就到了 Redis 服务器里。
     *
     * ③ Redis 广播
     *
     * Redis 收到频道 notify:like 的消息后，广播给所有订阅了 notify:* 的客户端。你的 listenerContainer 正好订阅了这个模式，所以消息被投递过来。
     *
     * ④ listenerAdapter 接收并转发
     *
     * listenerContainer 拿到原始字节消息，交给 listenerAdapter。adapter 把字节转成 String，然后反射调用：
     *
     *
     * NotificationMessageReceiver.onMessage("用户1001赞了你的文章88")
     * ⑤ onMessage 通过 SSE 推给用户 B
     *
     *
     * @Component
     * public class NotificationMessageReceiver {
     *
     *     @Autowired
     *     private SseEmitterManager sseManager;
     *
     *     public void onMessage(String message) {
     *         // 根据业务逻辑确定目标用户是 2002
     *         Long targetUserId = 2002L;
     *
     *         // 从管理器中拿到用户 B 对应的 SseEmitter
     *         SseEmitter emitter = sseManager.getEmitter(targetUserId);
     *
     *         // 通过 SSE 推送到用户 B 的浏览器
     *         emitter.send(SseEmitter.event()
     *                 .name("notification")
     *                 .data(message));
     *     }
     * }
     * ⑥ 用户 B 的浏览器收到消息
     *
     * 前端 JS 代码早就建好了 SSE 连接：
     *
     *
     * const es = new EventSource("/api/sse/notifications");
     * es.addEventListener("notification", (event) => {
     *     // event.data = "用户1001赞了你的文章88"
     *     showToast(event.data);
     * });
     * 用户 B 的页面弹出提示："用户1001赞了你的文章88"。
     *
     * 整个过程中每个角色的职责
     *
     * 点赞 Controller 是触发者，它只负责写数据库和往 Redis 丢一条消息，不关心通知怎么送达。redisTemplate.convertAndSend 就是"发送管理员"，Spring 自动配好了，一行代码就够。
     *
     * 你配的那个 listenerContainer + listenerAdapter 是接收端，负责监听 Redis 频道、反序列化消息、调用业务方法。
     *
     * onMessage 是桥梁，从 Redis 接收消息，再通过 SSE 推给对应的前端用户。
     *
     * SSE 是最后一公里，负责把消息从服务器送到浏览器。
     *
     * 这样设计的好处是，如果以后你想加评论通知、关注通知，只需要在对应的 Service 里多写一行 convertAndSend("notify:comment", ...) 就行，监听端不用改，因为它用的是 notify:* 通配符，新频道自动匹配。
     */


    /**
     * 这个配置类本质上就干了一件事：监听 Redis 频道，收到消息后交给业务方法处理。
     *
     * listenerContainer 负责"听"——持续监听所有匹配 notify:* 的 Redis 频道。listenerAdapter 负责"翻译"——把 Redis 的原始字节转成 Java 对象，再调用 onMessage 方法。
     *
     * 之所以不需要配"发送管理员"，是因为发送端用 Spring 自带的 RedisTemplate.convertAndSend() 一行代码就搞定，不需要额外配置。
     *
     * 整个架构是三层接力：业务模块通过 Redis 发消息（解耦）→ 你这个监听器接收消息（中转）→ 通过 SSE 推给前端（送达）。Redis 是内部消息总线，SSE 是推到浏览器的最后一公里，两者各司其职。
     */
}
