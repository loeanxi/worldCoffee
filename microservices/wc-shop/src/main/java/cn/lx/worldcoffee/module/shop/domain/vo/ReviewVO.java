package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价展示VO
 */
@Data
@Builder
public class ReviewVO {
    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    private Long productId;
    private String productName;
    private Integer rating;
    private String content;
    private List<String> images;
    private String adminReply;
    private LocalDateTime adminReplyTime;
    private LocalDateTime createTime;
}
