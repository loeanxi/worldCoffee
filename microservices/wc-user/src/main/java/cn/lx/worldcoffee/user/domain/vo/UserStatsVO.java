package cn.lx.worldcoffee.user.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserStatsVO {
    private Long postCount;
    private Long likeCount;
    private Long favoriteCount;
    private Long commentCount;
    private Long followingCount;
    private Long followerCount;
}
