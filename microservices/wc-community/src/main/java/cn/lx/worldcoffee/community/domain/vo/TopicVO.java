package cn.lx.worldcoffee.community.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopicVO {
    private Long id;
    private String name;
    private String description;
    private Integer postCount;
}
