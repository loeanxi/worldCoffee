package cn.lx.worldcoffee.module.shop.domain.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 申请退款表单
 */
@Data
public class RefundApplyForm {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "退款类型不能为空")
    @Min(value = 1, message = "退款类型非法")
    @Max(value = 2, message = "退款类型非法")
    private Integer type;          // 1仅退款 2退货退款

    @NotBlank(message = "退款原因不能为空")
    private String reason;

    private String trackingNo;
}
