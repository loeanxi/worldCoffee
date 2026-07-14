package cn.lx.worldcoffee.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.UUID;

/**
 * 分布式限流器（Redis Lua 滑动窗口）
 * 微服务架构下：网关 JWT 过滤器校验 token 后传 X-User-Id 头，下游服务直接读头获取 userId
 */
@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

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
            return true; // Redis 异常时放行
        }
    }

    /**
     * 微服务改造：从网关转发的 X-User-Id 请求头获取用户ID，不再依赖 SecurityContext
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        try {
            String userIdStr = request.getHeader("X-User-Id");
            if (userIdStr != null && !userIdStr.isEmpty()) {
                return Long.valueOf(userIdStr);
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
