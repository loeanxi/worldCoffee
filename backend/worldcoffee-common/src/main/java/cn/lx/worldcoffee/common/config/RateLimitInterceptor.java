package cn.lx.worldcoffee.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//限流器:现在秒杀接口 /api/shop/seckill/** 会被令牌桶限流，超过 200 QPS 返回 429。
//更改：
//分布式限流：全局 + 用户级
@Component
@Slf4j
//@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<Long> rateLimitScript;


    // 手动构造器，去掉 @RequiredArgsConstructor
    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setLocation(new ClassPathResource("rate_limit.lua"));
        this.rateLimitScript.setResultType(Long.class);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 只对秒杀 buy 接口限流
        if (!path.contains("/seckill/buy")) return true;

        Long userId = getCurrentUserId(request);
        String clientIp = getClientIp(request);

        // 全局限流：每秒 100
        boolean globalPass = tryAcquire("rate:seckill:buy:global", 1000, 100);
        if (!globalPass) {
            write429(response, "秒杀太火爆，请稍后再试");
            return false;
        }

        // 用户级限流：每 60 秒 5 次
        if (userId != null) {
            boolean userPass = tryAcquire("rate:seckill:buy:user:" + userId, 60 * 1000, 5);
            if (!userPass) {
                write429(response, "操作太频繁，请稍后再试");
                return false;
            }
        }

        // IP 级限流：每 60 秒 10 次（兜底，防止未登录刷）
        boolean ipPass = tryAcquire("rate:seckill:buy:ip:" + clientIp, 60 * 1000, 10);
        if (!ipPass) {
            write429(response, "操作太频繁，请稍后再试");
            return false;
        }

        return true;
    }


//
//
//    static class TokenBucket {
//        private final int capacity;
//        private final int refillRate;
//        private long tokens;
//        private long lastRefillTime;
//
//        public TokenBucket(int capacity, int refillRate) {
//            this.capacity = capacity;
//            this.refillRate = refillRate;
//            this.tokens = capacity;
//            this.lastRefillTime = System.nanoTime();
//        }
//
//        public synchronized boolean tryAcquire() {
//            refill();
//            if (tokens > 0) {
//                tokens--;
//                return true;
//            }
//            //// 429
//            return false;
//        }
//
//        private void refill() {
//            long now = System.nanoTime();
//            long elapsed = now - lastRefillTime;
//            long tokensToAdd = (long) (elapsed / 1_000_000_000.0 * refillRate);
//            if (tokensToAdd > 0) {
//                tokens = Math.min(capacity, tokens + tokensToAdd);
//                lastRefillTime = now;
//            }
//        }
//    }

    private void write429(HttpServletResponse response, String msg) throws Exception {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(429);
        response.getWriter().write("{\"code\":429,\"msg\":\"" + msg + "\",\"data\":null}");
    }

    private boolean tryAcquire(String key, long windowMs, int limit) {
        String unique = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        try {
            Long result = redisTemplate.execute(rateLimitScript, List.of(key),
                    String.valueOf(windowMs), String.valueOf(limit), String.valueOf(now), unique);
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("限流脚本执行异常，key={}", key, e);
            // Redis 异常时放行，避免误杀
            return true;
        }
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return Long.valueOf(auth.getPrincipal().toString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
/**
 * 单机限流：如果部署 3 个实例，每个实例都能处理 200 QPS，理论上全局变成 600 QPS
 * 没有用户级限流：一个用户狂刷能把桶占满
 * /seckill/activities 列表接口也被限流了：因为路径包含 /seckill，但实际上只有 /buy 需要限
 * 所以如果你想让限流更靠谱，建议改成：
 *
 * 按用户限流
 * 只限制 /buy
 * 用 Redis/Redisson 做分布式限流
 */
