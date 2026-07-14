package cn.lx.worldcoffee.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coffee_post_topic")
public class CoffeePostTopic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long topicId;
    private LocalDateTime createTime;
}
