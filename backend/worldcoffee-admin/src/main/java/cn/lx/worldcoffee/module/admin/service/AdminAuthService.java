package cn.lx.worldcoffee.module.admin.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.JwtUtil;
import cn.lx.worldcoffee.module.admin.domain.AdminLoginForm;
import cn.lx.worldcoffee.module.admin.domain.AdminLoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 管理员认证服务
 *
 * 职责：处理管理员登录/登出，校验管理员凭据，签发带 ADMIN role 的 JWT
 *
 * 设计：
 *   - 管理员账号配置在 application.yaml（admin.username / admin.password）
 *   - 密码明文比对（开发阶段），生产环境建议改为 BCrypt
 *   - 登录成功后签发 JWT，额外携带 role=ADMIN claim
 *   - JwtAuthenticationFilter 解析到 role=ADMIN 后授予 ADMIN_ROLE 权限
 *   - SecurityConfig 用 .hasAuthority("ADMIN_ROLE") 保护 /api/admin/** 端点
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    /**
     * 管理员登录
     * 校验用户名密码 → 签发带 role=ADMIN 的 JWT
     */
    public AdminLoginVO login(AdminLoginForm form) {
        // 1. 校验用户名
        if (!adminUsername.equals(form.getUsername())) {
            throw new ServiceException("管理员账号或密码错误");
        }
        // 2. 校验密码（明文比对，开发阶段够用）
        if (!adminPassword.equals(form.getPassword())) {
            throw new ServiceException("管理员账号或密码错误");
        }
        // 3. 签发 JWT，带 role=ADMIN
        //    用 "admin" 作为 userId（管理员不是普通用户，不会和普通用户 ID 冲突）
        String token = jwtUtil.generateToken("admin", adminUsername, "ADMIN");

        return AdminLoginVO.builder()
                .token(token)
                .username(adminUsername)
                .build();
    }

    /**
     * 管理员登出
     * 把 token 加入 Redis 黑名单，和普通的 logout 机制一样
     */
    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring(7);
        // 加入黑名单，TTL = JWT 剩余有效期
        redisTemplate.opsForSet().add("token:blacklist", token);
        Long ttl = jwtUtil.getExpireTime() / 1000;
        redisTemplate.expire("token:blacklist", ttl, TimeUnit.SECONDS);
    }
}
