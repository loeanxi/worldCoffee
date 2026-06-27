package cn.lx.worldcoffee.module.shop.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrderFrom {
    @NotBlank(message = "收货地址不能为空")
    private String address;

    private String remark;  // 备注选填
}
