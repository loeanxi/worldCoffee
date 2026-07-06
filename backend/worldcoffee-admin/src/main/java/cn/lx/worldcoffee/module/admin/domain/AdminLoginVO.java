package cn.lx.worldcoffee.module.admin.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 管理员登录响应
 * 返回 JWT token 和管理员用户名
 */
@Data
@Builder
public class AdminLoginVO {
    private String token;
    private String username;
}
