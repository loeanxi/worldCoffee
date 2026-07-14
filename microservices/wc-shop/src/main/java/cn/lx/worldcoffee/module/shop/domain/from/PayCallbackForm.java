package cn.lx.worldcoffee.module.shop.domain.from;

import lombok.Data;

@Data
public class PayCallbackForm {
    private String orderNo;
    private String transactionId;
}
