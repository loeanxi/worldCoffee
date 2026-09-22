package cn.lx.worldcoffee.module.shop.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderFrom {
    @NotBlank(message = "收货地址不能为空")
    private String address;

    private String remark;  // 备注选填

    private Long couponId;  // 使用的优惠券ID

    /**
     * 直购商品ID（选填）：不为空时跳过购物车，直接按该商品下单，
     * 且不清空购物车。用于小程序/移动端「立即购买」。
     */
    private Long productId;

    /** 直购数量，配合 productId 使用，缺省 1 */
    private Integer quantity;

    /**
     * 勾选的购物车项ID列表（选填）：不为空时只消费这些购物车项，
     * 并只删除这些项；为空时沿用旧行为（消费并清空整个购物车）。
     */
    private List<Long> cartIds;
}
