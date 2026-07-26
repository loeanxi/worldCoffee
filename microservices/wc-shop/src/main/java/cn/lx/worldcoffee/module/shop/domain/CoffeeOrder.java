package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coffee_order")
public class CoffeeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;         // 订单编号
    private Long userId;            // 用户ID
    private BigDecimal totalAmount; // 总金额
    private Integer status;         // 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消
    private LocalDateTime payTime;  // 支付时间
    private String address;         // 收货地址
    private String remark;          // 备注
    private LocalDateTime createTime;

    // 优惠券相关
    private Long couponId;          // 使用的优惠券ID
    private BigDecimal discountAmount; // 优惠金额


    // ↓↓↓ 新增物流字段 ↓↓↓
    private String shippingCompany; // 快递公司
    private String trackingNo;      // 快递单号
    private LocalDateTime shippedTime;   // 发货时间
    private LocalDateTime deliveredTime; // 收货时间

    // 退款相关
    private Integer refundStatus;    // 退款状态 0无 1退款中 2已退款
}
