package cn.lx.worldcoffee.module.notification.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationVO {
    private Long id;
    private String senderName;   // 谁
    private String type;         // 做了什么
    private String content;      // 摘要
    private Long postId;         // 点进去跳到对应帖子
    private Boolean isRead;      // 读了没
    private Long senderId;  // 触发者的用户ID，FOLLOW 类通知跳转主页用
    private LocalDateTime createTime;
}
