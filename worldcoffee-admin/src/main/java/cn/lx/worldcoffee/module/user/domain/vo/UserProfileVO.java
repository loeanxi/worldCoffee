package cn.lx.worldcoffee.module.user.domain.vo;

import cn.lx.worldcoffee.module.coffee.domain.vo.PostListVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserProfileVO {
    private Long id;
    private String username;
    private String avatar;  // 头像URL
    private Integer postCount;             // 发了多少帖子
    private LocalDateTime createTime;      // 注册时间
    private List<PostListVO> recentPosts;  // 最近N条帖子
    private Boolean isFollowing;  // 当前登录用户是否关注了TA
}
