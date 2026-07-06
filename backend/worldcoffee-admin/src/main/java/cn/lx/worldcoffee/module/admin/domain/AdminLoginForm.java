package cn.lx.worldcoffee.module.admin.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员登录表单
 * 管理员通过 /api/admin/login 登录，传入用户名 + 密码
 */
@Data
public class AdminLoginForm {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
