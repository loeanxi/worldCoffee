package cn.lx.worldcoffee.message.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationVO {
    private Long id;
    private String senderName;
    private String avatar;
    private String type;
    private String content;
    private Long postId;
    private Boolean isRead;
    private Long senderId;
    private LocalDateTime createTime;
}
