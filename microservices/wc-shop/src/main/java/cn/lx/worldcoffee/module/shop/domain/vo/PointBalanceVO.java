package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 积分余额VO
 */
@Data
@Builder
public class PointBalanceVO {
    private Integer points;
    private Integer totalPoints;
    private Integer memberLevel;
    private String memberLevelDesc;
}
