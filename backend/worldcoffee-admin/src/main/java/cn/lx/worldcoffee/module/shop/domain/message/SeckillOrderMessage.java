package cn.lx.worldcoffee.module.shop.domain.message;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class SeckillOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long couponId;
    private Long productId;
    private String orderNo;
    private String address;
    private String remark;
    private BigDecimal seckillPrice;
}
