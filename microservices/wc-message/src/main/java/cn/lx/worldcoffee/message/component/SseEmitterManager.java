package cn.lx.worldcoffee.message.component;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseEmitterManager {
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> sseMap = new ConcurrentHashMap<>();

    public void addEmitter(String userId, SseEmitter emitter) {
        sseMap.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> emitters = sseMap.get(userId);
        if (emitters != null) emitters.remove(emitter);
    }

    public void sendNotification(String userId, String message) {
        List<SseEmitter> emitters = sseMap.get(userId);
        if (emitters != null) {
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("notification").data(message));
                } catch (IOException e) {
                    dead.add(emitter);
                }
            }
            emitters.removeAll(dead);
        }
    }
}
