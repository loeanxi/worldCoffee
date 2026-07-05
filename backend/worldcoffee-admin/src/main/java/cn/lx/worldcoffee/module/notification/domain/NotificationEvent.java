package cn.lx.worldcoffee.module.notification.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationEvent {
    private Long receiverId;    // 谁收通知
    private Long senderId;      // 谁触发的
    private String type;        // LIKE / COMMENT / FOLLOW
    private Long postId;        // 关联帖子ID
    private Long commentId;     // 关联评论ID
    private String content;     // "lx 赞了你的帖子"
}
