package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderVO {
    private Long id;
    private String orderNo;         // 订单编号
    private Long userId;            // 所属用户ID（admin 后台需要）
    private BigDecimal totalAmount; // 总金额
    private Integer status;         // 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消
    private String address;         // 收货地址
    private String remark;          // 备注
    private LocalDateTime createTime;
    private List<OrderItemVO> items;// 订单明细列表

    // 优惠券相关
    private Long couponId;          // 使用的优惠券ID
    private BigDecimal discountAmount; // 优惠金额
    private String couponName;      // 优惠券名称

    @Data
    @Builder
    public static class OrderItemVO {
        private Long productId;
        private String productName;
        private BigDecimal price;
        private Integer quantity;
    }
}
