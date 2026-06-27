package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CartVO {
    private Long id;               // cart_item表主键
    private Long productId;        // 商品ID
    private String productName;    // 商品名
    private BigDecimal price;      // 单价
    private String image;          // 商品首图
    private Integer quantity;      // 数量
    private Integer stock;         // 当前库存，前端用来限制最大数量
}
