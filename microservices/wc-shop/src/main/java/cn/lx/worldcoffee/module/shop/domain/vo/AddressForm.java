package cn.lx.worldcoffee.module.shop.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AddressForm {
    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    @NotBlank(message = "请选择省份")
    private String province;

    @NotBlank(message = "请选择城市")
    private String city;

    @NotBlank(message = "请选择区县")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    private Boolean isDefault;  // 是否设为默认地址
}
