package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CouponVO {
    private Long id;
    private String name;
    private Integer type;           // 1-满减券 2-折扣券 3-秒杀券
    private BigDecimal value;
    private BigDecimal minAmount;
    private Integer stock;
    private Boolean claimed;        // 当前用户是否已领取
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private BigDecimal seckillPrice;  // 秒杀价
    private Long productId;         // 秒杀商品ID
    private String productName;     // 秒杀商品名称
    private String productImage;    // 秒杀商品图片
}
