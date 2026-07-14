package cn.lx.worldcoffee.module.shop.domain.from;

import lombok.Data;

@Data
public class SeckillForm {
    private Long couponId;
    private Long productId;
    private String address;
    private String remark;
    private String seckillToken;

    public CreateOrderFrom toCreateOrderForm() {
        CreateOrderFrom form = new CreateOrderFrom();
        form.setCouponId(couponId);
        form.setAddress(address);
        form.setRemark(remark);
        return form;
    }
}


