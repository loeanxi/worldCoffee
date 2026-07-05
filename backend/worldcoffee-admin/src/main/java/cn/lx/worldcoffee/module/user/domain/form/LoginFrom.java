package cn.lx.worldcoffee.module.user.domain.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginFrom {

    @NotBlank(message = "用户名格式错误")
    private String username;

    @NotBlank(message = "密码格式错误")
    private String password;
}
