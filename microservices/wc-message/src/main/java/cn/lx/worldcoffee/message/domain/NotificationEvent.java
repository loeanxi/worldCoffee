package cn.lx.worldcoffee.message.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationEvent {
    private Long receiverId;
    private Long senderId;
    private String type;
    private Long postId;
    private Long commentId;
    private String content;
}
