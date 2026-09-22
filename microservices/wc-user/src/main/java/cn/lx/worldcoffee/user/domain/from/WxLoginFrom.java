package cn.lx.worldcoffee.user.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginFrom {

    /** wx.login 返回的临时登录凭证 */
    @NotBlank(message = "code 不能为空")
    private String code;
}
