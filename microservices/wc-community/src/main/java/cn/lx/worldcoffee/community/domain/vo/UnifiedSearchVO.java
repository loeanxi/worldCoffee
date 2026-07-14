package cn.lx.worldcoffee.community.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UnifiedSearchVO {
    private String keyword;
    private List<PostListVO> posts;
    private List<TopicVO> topics;
}
