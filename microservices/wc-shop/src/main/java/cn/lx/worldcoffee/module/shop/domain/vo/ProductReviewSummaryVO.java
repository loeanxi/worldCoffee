package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 商品评分聚合VO
 */
@Data
@Builder
public class ProductReviewSummaryVO {
    private Long productId;
    private Double averageRating;
    private Long totalCount;
    private Map<Integer, Long> ratingDistribution;
    private Double goodRate;
}
