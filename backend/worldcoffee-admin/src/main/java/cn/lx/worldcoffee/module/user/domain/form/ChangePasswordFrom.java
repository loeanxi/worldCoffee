package cn.lx.worldcoffee.module.user.domain.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class ChangePasswordFrom {
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Length(min = 6, message = "新密码至少6位")
    private String newPassword;
}
