package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentResultVO {
    private Long orderId;       // 新增
    private String orderNo;
    private String transactionId;
    private BigDecimal amount;
    private String payUrl;      // Mock 支付链接，前端可显示"点击模拟支付"
    private Integer status;     // 0-待支付
}
