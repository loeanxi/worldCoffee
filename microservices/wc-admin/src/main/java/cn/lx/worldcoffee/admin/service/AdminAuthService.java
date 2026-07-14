package cn.lx.worldcoffee.admin.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.result.Constant;
import cn.lx.worldcoffee.common.security.JwtUtil;
import cn.lx.worldcoffee.module.shop.domain.admin.AdminLoginForm;
import cn.lx.worldcoffee.module.shop.domain.admin.AdminLoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:admin123}")
    private String adminPassword;

    public AdminLoginVO login(AdminLoginForm form) {
        if (!adminUsername.equals(form.getUsername()) || !adminPassword.equals(form.getPassword())) {
            throw new ServiceException("管理员账号或密码错误");
        }
        String token = jwtUtil.generateToken(0L, adminUsername);
        return AdminLoginVO.builder()
                .token(token)
                .username(adminUsername)
                .build();
    }

    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return;
        String token = authHeader.substring(7);
        redisTemplate.opsForSet().add("token:blacklist", token);
        redisTemplate.expire("token:blacklist", Constant.JWT_EXPIRATION, TimeUnit.MILLISECONDS);
    }
}
