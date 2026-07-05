package cn.lx.worldcoffee.module.message.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionVO {
    private Long userId;          // 跟谁聊（对方的ID）
    private String username;      // 对方名字
    private String avatar;        // 对方头像
    private String lastMessage;   // 最后一条消息的预览
    private LocalDateTime lastTime; // 最后一条消息的时间
    private Long unreadCount;     // 未读条数

    //这个就是微信聊天列表里一行的数据：对方的头像+名字 + 最后一条消息 + 未读数。
}
