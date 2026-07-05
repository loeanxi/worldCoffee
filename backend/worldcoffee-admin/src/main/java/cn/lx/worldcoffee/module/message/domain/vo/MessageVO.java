package cn.lx.worldcoffee.module.message.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder  // 可以用 .id().content().build() 链式创建对象
public class MessageVO {
    private Long id;
    private Long fromId;       // 谁发的
    private String fromName;   // 发消息的人叫啥（查用户表填进去）
    private String fromAvatar; // 发消息的人头像
    private Long toId;         // 发给谁
    private String content;    // 消息内容
    private Integer messageType; // 1=文本 2=图片
    private Boolean isRead;    // true=已读 false=未读
    private LocalDateTime createTime;
    //为什么 isRead 用 Boolean 而不是 Integer？ 前端拿 true/false 比 0/1 更直观。
}
