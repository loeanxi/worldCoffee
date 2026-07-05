package cn.lx.worldcoffee.module.user.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BindPhoneFrom {

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String Phone;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
