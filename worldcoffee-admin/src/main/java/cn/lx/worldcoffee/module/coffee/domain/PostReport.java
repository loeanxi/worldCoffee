package cn.lx.worldcoffee.module.coffee.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_report")
public class PostReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;       // 被举报帖子ID
    private Long reporterId;   // 举报人ID
    private String reason;     // 举报原因
    private Integer status;    // 0=未处理 1=已处理
    private LocalDateTime createTime;
}
