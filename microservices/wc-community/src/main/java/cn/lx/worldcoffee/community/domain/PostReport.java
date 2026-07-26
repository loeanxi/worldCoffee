package cn.lx.worldcoffee.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_report")
public class PostReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long reporterId;
    private String reason;
    private Integer status;
    private String remark;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
