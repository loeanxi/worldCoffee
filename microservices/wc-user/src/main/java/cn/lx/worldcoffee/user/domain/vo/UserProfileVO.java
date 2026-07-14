package cn.lx.worldcoffee.user.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserProfileVO {
    private Long id;
    private String username;
    private String avatar;
    private Integer postCount;
    private Integer followingCount;
    private Integer followerCount;
    private Boolean isFollowing;
    private LocalDateTime createTime;
    private List<UserPostVO> recentPosts;
}
