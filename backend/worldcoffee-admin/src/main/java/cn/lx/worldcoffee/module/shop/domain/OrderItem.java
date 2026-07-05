package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("order_item")
public class OrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;           // 订单ID
    private Long productId;         // 商品ID
    private String productName;     // 商品名称快照（下单时的名称，防止商品改名后历史订单对不上）
    private BigDecimal price;       // 下单时单价
    private Integer quantity;       // 数量

//    价格用 BigDecimal：不是 float/double，钱的计算不能丢精度
//    OrderItem.productName 存快照：下单时复制商品名和单价，防止商品改名/涨价后历史订单也跟着变
//    CartItem 表有 uk_user_product 唯一约束：同一用户同一商品不重复加，改数量就行
}
