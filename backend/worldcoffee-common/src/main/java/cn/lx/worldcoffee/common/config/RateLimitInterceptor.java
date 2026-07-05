package cn.lx.worldcoffee.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;

//限流器:现在秒杀接口 /api/shop/seckill/** 会被令牌桶限流，超过 200 QPS 返回 429。
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        if (!path.contains("/seckill")) return true;

        TokenBucket bucket = buckets.computeIfAbsent(path, k -> new TokenBucket(200, 20));
        if (!bucket.tryAcquire()) {
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(429);
            response.getWriter().write("{\"code\":429,\"msg\":\"秒杀太火爆，请稍后再试\",\"data\":null}");
            return false;
        }
        return true;
    }

    static class TokenBucket {
        private final int capacity;
        private final int refillRate;
        private long tokens;
        private long lastRefillTime;

        public TokenBucket(int capacity, int refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillTime = System.nanoTime();
        }

        public synchronized boolean tryAcquire() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillTime;
            long tokensToAdd = (long) (elapsed / 1_000_000_000.0 * refillRate);
            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTime = now;
            }
        }
    }
}
