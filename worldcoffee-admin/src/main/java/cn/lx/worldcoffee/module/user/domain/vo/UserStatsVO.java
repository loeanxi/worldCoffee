package cn.lx.worldcoffee.module.user.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserStatsVO {
    private Long postCount;        // 发帖数
    private Long likeCount;        // 收到的赞总数
    private Long favoriteCount;    // 被收藏总数
    private Long commentCount;     // 收到的评论数
    private Long followingCount;   // 关注了多少人
    private Long followerCount;    // 有多少粉丝
}