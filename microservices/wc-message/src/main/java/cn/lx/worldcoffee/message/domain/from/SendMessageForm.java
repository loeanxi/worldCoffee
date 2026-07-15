package cn.lx.worldcoffee.message.domain.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送私信表单
 */
@Data
public class SendMessageForm {
    @NotNull(message = "接收者ID不能为空")
    private Long toId;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    private Integer messageType;
}
