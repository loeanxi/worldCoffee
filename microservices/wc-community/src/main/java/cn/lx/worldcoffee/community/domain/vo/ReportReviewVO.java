package cn.lx.worldcoffee.community.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReportReviewVO {
    private Long id;
    private Long postId;
    private String postTitle;
    private String postContent;
    private List<String> postImages;
    private Long postAuthorId;
    private Long reporterId;
    private String reason;
    private Integer status;
    private String statusText;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}
