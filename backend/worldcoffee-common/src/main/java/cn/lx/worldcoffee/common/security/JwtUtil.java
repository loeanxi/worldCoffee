package cn.lx.worldcoffee.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;
    @Value("${jwt.expire-time}")
    private Long expireTime;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId, String username) {
        return generateToken(userId, username, null);
    }

    /**
     * 签发 JWT（支持角色）
     * role 为 null 时和两参数版本完全一样，不影响普通用户的 token
     * 管理员登录时传 "ADMIN"，JwtFilter 解析后授予 ADMIN_ROLE 权限
     */
    public String generateToken(String userId, String username, String role) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expireTime);
        var builder = Jwts.builder()
                .setSubject(userId)
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(expireDate);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(getKey()).compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getExpireTime() {
        return expireTime;
    }
}
