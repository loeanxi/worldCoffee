package cn.lx.worldcoffee.common.redis;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

//消息接收器
@Component
//这个类是上面 Redis 订阅链路的终点——收到消息后，通过 SSE（Server-Sent Events） 把通知实时推送给前端用户。
//核心数据结构 sseMap
public class NotificationMessageReceiver {
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> sseMap = new ConcurrentHashMap<>();
    //key 是用户ID，value 是该用户当前所有活跃的 SSE 连接。用 CopyOnWriteArrayList 是因为可能多线程同时操作（发消息的同时用户断开连接要移除），它自带线程安全。
    //同一个用户可能有多个连接（比如开了多个浏览器标签页），所以是 List。

    //onMessage — 收到 Redis 消息时调用
    //这就是上面配置里 adapter 指向的方法。
    //逻辑很直白：从频道名 notify:3 里截出用户ID 3，找到这个用户的所有 SSE 连接，逐个把消息推过去。推送失败说明连接已断开，顺手移除。
    public void onMessage(String message, String channel) {
        // channel格式: "notify:3"
        String userId = channel.substring(7);
        List<SseEmitter> emitters = sseMap.get(userId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().data(message));
                } catch (Exception e) {
                    emitters.remove(emitter);
                }
            }
        }
    }

    //addEmitter — 用户建立 SSE 连接时调用
    public void addEmitter(String userId, SseEmitter emitter) {
        sseMap.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        //computeIfAbsent 的意思是：这个用户还没有连接列表就新建一个，有就直接往里加。前端通过某个接口建立 SSE 连接时，会调这个方法把自己的 SseEmitter 注册进来。
    }

    //    removeEmitter — 用户断开连接时清理
    //    把对应的 emitter 从列表里删掉，防止内存泄漏和无效推送。
    public void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> emitters = sseMap.get(userId);
        if (emitters != null) emitters.remove(emitter);
    }
    /**
     * 整个通知链路串起来就是：
     *
     *
     * 有人点赞/评论/关注
     *     ↓
     * 发布消息到 Redis 频道 notify:{userId}
     *     ↓
     * NotificationMessageReceiver.onMessage 收到消息
     *     ↓
     * 从 sseMap 找到目标用户的 SSE 连接
     *     ↓
     * 实时推送到前端浏览器
     *
     * 这就是一个典型的"Redis Pub/Sub + SSE"实时通知方案。
     *
     */
}
