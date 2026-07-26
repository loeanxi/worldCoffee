package cn.lx.worldcoffee.community.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostDraftVO {
    private Long id;
    private String title;
    private String content;
    private List<String> images;
    private String noteType;
    private String videoUrl;
    private String coverUrl;
    private Integer videoDuration;
    private String coffeeName;
    private String coffeeBrand;
    private String location;
    private List<String> topics;
    private List<Long> productIds;
    private LocalDateTime updateTime;
}
