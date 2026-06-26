package cn.lx.worldcoffee.module.coffee.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostDetailVO {
    private Long id;
    private Long userId;
    private String username; // 发帖人昵称
    private String title;
    private String content;           // 完整内容，不截取
    private List<String> images;
    private String coffeeName;
    private String coffeeBrand;
    private String location;
    private Integer postType;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private Boolean likedByMe;        // 当前用户是否已点赞
    private Boolean favoritedByMe;    // 当前用户是否已收藏
    private LocalDateTime createTime;
    private List<CommentVO> comments; // 评论列表

}
