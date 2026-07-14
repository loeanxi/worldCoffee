package cn.lx.worldcoffee.user.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FollowingVO {
    private Long id;
    private String username;
    private String avatar;
    private Boolean isFollowing;
}
