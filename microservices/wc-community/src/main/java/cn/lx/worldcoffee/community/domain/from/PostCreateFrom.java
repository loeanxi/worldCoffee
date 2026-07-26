package cn.lx.worldcoffee.community.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class PostCreateFrom {
    @NotBlank(message = "标题不能为空")
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
}
