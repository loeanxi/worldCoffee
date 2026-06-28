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
                // === 新增：检查黑名单 ===
                Boolean isBlacklisted = stringRedisTemplate.opsForSet().isMember("token:blacklist", token);
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    filterChain.doFilter(request, response);
                    return;  // 不放行 return; 表示"我不往下传了，直接结束"
                }
                Claims claims = jwtUtil.parseToken(token);
                String userId = claims.getSubject();
                String username = claims.get("username", String.class);

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                auth.setDetails(username);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                //token无效，不设置认证 -> 后面security返回401
            }
        }

        filterChain.doFilter(request,response);
    }
}