package cn.lx.worldcoffee.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 优先从 Authorization header 取 token
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")){
            token = header.substring(7);
        }
        // 新增 query parameter 取 token 的降级逻辑，解决 SSE EventSource 无法发送自定义 header 导致认证失败的问题。
        // 2. 降级：从 query parameter 取 token（SSE 的 EventSource 不支持自定义 header）
        if (token == null) {
            token = request.getParameter("token");
        }

        if (token != null) {
            try {
                // === 1. 检查 token 黑名单（Redis 失败时降级跳过，避免把合法用户拦截） ===
                boolean blacklisted = false;
                try {
                    Boolean r = stringRedisTemplate.opsForSet().isMember("token:blacklist", token);
                    blacklisted = Boolean.TRUE.equals(r);
                } catch (Exception redisEx) {
                    // Redis 不可用时降级：不拦截，继续解析 JWT
                    System.out.println("[JwtFilter] Redis 不可用，跳过黑名单检查: " + redisEx.getMessage());
                }

                if (blacklisted) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // === 2. 解析 JWT ===
                Claims claims = jwtUtil.parseToken(token);
                String userId = claims.getSubject();
                String username = claims.get("username", String.class);

                if (userId != null) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(userId, null, List.of());
                    auth.setDetails(username);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // JWT 本身无效 / 过期：不清空上下文，保持匿名状态即可
                System.out.println("[JwtFilter] token 解析失败: " + e.getMessage());
            }
        }

        filterChain.doFilter(request,response);
    }
}