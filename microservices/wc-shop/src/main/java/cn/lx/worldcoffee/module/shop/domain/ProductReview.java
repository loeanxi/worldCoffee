package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价实体
 */
@Data
@TableName("product_review")
public class ProductReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long orderItemId;
    private Long productId;
    private Long userId;
    private Integer rating;          // 1-5
    private String content;
    private String images;           // 图片JSON数组字符串
    private Integer isAnonymous;     // 0否 1是
    private Integer status;          // 1正常 0隐藏
    private String adminReply;
    private LocalDateTime adminReplyTime;
    private LocalDateTime createTime;
}
