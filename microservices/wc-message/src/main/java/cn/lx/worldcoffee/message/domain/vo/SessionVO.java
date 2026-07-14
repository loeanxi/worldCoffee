package cn.lx.worldcoffee.message.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionVO {
    private Long userId;
    private String username;
    private String avatar;
    private String lastMessage;
    private LocalDateTime lastTime;
    private long unreadCount;
}
