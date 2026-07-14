package cn.lx.worldcoffee.community.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentVO {
    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    private String content;
    private LocalDateTime createTime;
}
