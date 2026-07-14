package cn.lx.worldcoffee.user.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterForm {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    private String phone;
}
