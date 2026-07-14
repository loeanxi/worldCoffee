package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("coupon_product")
public class CouponProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long couponId;
    private Long productId;
}
