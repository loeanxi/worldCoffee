package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分兑换规则实体
 */
@Data
@TableName("point_exchange_rule")
public class PointExchangeRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer type;       // 1优惠券
    private Integer requiredPoints;
    private Long couponId;
    private Integer stock;      // -1无限
    private Integer status;
    private LocalDateTime createTime;
}
