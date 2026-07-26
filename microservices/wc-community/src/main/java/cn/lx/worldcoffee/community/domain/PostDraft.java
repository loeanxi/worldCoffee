package cn.lx.worldcoffee.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_draft")
public class PostDraft {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String images;
    private String noteType;
    private String videoUrl;
    private String coverUrl;
    private Integer videoDuration;
    private String coffeeName;
    private String coffeeBrand;
    private String location;
    private String topics;
    private String productIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
