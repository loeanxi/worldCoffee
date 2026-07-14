package cn.lx.worldcoffee.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coffee_topic")
public class CoffeeTopic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Integer postCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
