package cn.lx.worldcoffee.module.user.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FollowingVO {
    private Long id;
    private String username;
    private String avatar;
    private Boolean isFollowing;  // 当前登录用户是否也关注了TA
}
