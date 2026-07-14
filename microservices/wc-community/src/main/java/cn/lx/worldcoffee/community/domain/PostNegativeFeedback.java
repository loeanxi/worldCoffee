package cn.lx.worldcoffee.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_negative_feedback")
public class PostNegativeFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sessionId;
    private Long postId;
    private String reasonType;
    private String reason;
    private LocalDateTime createTime;
}
