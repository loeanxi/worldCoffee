package cn.lx.worldcoffee.module.shop.domain.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 提交评价表单
 */
@Data
public class ReviewForm {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotEmpty(message = "评价项不能为空")
    private List<ReviewItem> items;

    @Data
    public static class ReviewItem {
        @NotNull(message = "订单项ID不能为空")
        private Long orderItemId;

        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最低1星")
        @Max(value = 5, message = "评分最高5星")
        private Integer rating;

        private String content;
        private List<String> images;
        private Boolean anonymous;
    }
}
