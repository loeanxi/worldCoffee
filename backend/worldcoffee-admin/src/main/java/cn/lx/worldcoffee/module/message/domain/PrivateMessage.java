package cn.lx.worldcoffee.module.message.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("private_message")
public class PrivateMessage {
    @TableId(type = IdType.AUTO)  // id 是自增的
    private Long id;

    private Long fromId;          // 发送人ID
    private Long toId;            // 接收人ID
    private String content;       // 消息内容
    private Integer messageType;  // 1=文本 2=图片
    private Integer isRead;       // 0=未读 1=已读
    private LocalDateTime createTime;
}
