package cn.lx.worldcoffee.module.coffee.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coffee_comment_like")
public class CoffeeCommentLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long commentId;
    private Long userId;
    private LocalDateTime createTime;
}
