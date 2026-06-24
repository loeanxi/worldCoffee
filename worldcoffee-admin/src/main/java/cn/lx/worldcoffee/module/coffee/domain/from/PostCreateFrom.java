package cn.lx.worldcoffee.module.coffee.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class PostCreateFrom {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;    // 正文（可为空）

    private List<String> images;    // 图片URL列表

    private String coffeeName;      // 咖啡名称

    private String coffeeBrand;     // 品牌

    private String location;        // 打卡地点

    @NotBlank(message = "帖子不能为空")
    private Integer postType;        // 1=图文 2=打卡

}
