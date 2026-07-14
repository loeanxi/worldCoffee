package cn.lx.worldcoffee.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coffee_post_product")
public class PostProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long productId;
    private LocalDateTime createTime;
}
