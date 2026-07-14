package cn.lx.worldcoffee.community.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostDetailVO {
    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    private String title;
    private String content;
    private List<String> images;
    private String coffeeName;
    private String coffeeBrand;
    private String location;
    private Integer postType;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private Boolean likedByMe;
    private Boolean favoritedByMe;
    private LocalDateTime createTime;
    private List<CommentVO> comments;
}
