package cn.lx.worldcoffee.community.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class CommentCreateFrom {
    @NotBlank(message = "评论不能为空")
    @Length(max = 500, message = "评论最多500字")
    private String content;
}
