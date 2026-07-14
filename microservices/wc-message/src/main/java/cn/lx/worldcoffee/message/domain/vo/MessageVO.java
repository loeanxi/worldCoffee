package cn.lx.worldcoffee.message.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageVO {
    private Long id;
    private Long fromId;
    private String fromName;
    private String fromAvatar;
    private Long toId;
    private String content;
    private Integer messageType;
    private Boolean isRead;
    private LocalDateTime createTime;
}
