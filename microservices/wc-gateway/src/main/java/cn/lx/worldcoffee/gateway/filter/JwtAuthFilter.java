package cn.lx.worldcoffee.gateway.filter;

import cn.lx.worldcoffee.common.result.Constant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final SecretKey key;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/shop/products",
            "/api/shop/categories",
            "/api/shop/seckill",
            "/api/user/login",
            "/api/user/register",
            "/api/users/login",
            "/api/users/register",
            "/api/admin/login",
            "/uploads/",
            "/actuator/"
    );

    private static final List<String> OPTIONAL_AUTH_LIST = Arrays.asList(
            "/api/coffee/posts/recommend",
            "/api/coffee/feed-events"
    );

    public JwtAuthFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.key = Keys.hmacShaKeyFor(Constant.JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();

        if (OPTIONAL_AUTH_LIST.contains(path)) {
            return filterOptionalAuth(exchange, chain);
        }

        for (String white : WHITE_LIST) {
            if (path.startsWith(white)) {
                return chain.filter(exchange);
            }
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "not logged in");
        }

        String token = authHeader.substring(7);
        return filterRequiredAuth(exchange, chain, token);
    }

    private Mono<Void> filterOptionalAuth(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        return filterRequiredAuth(exchange, chain, authHeader.substring(7));
    }

    private Mono<Void> filterRequiredAuth(ServerWebExchange exchange, GatewayFilterChain chain, String token) {
        return redisTemplate.opsForSet()
                .isMember("token:blacklist", token)
                .onErrorReturn(false)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return unauthorized(exchange, "token invalid");
                    }
                    return continueWithToken(exchange, chain, token);
                });
    }

    private Mono<Void> continueWithToken(ServerWebExchange exchange, GatewayFilterChain chain, String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);

            ServerHttpRequest newRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Username", username == null ? "" : username)
                    .build();

            return chain.filter(exchange.mutate().request(newRequest).build());
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "login expired");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        String body = "{\"code\":401,\"message\":\"" + message + "\"}";
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
