package cn.lx.worldcoffee.community.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentVO {
    private Long id;
    private Long userId;
    private Long parentId;
    private Long rootId;
    private Long replyToUserId;
    private String replyToUsername;
    private String username;
    private String avatar;
    private String content;
    private Integer likeCount;
    private java.util.List<CommentVO> replies;
    private LocalDateTime createTime;
}
