package cn.lx.worldcoffee.module.coffee.domain.vo;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostListVO { private Long id;
    private Long userId;
    private String username;          // 发帖人昵称
    private String title;
    private String content;           // 截取前100字预览
    private List<String> images;      // 图片列表
    private String coffeeName;        // 咖啡名称
    private String coffeeBrand;       // 品牌
    private String location;          // 打卡地点（打卡类型才有）
    private Integer postType;         // 1=图文 2=打卡
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private Boolean likedByMe;        // 当前用户是否已点赞（需登录才返回）
    private Boolean favoritedByMe;    // 当前用户是否已收藏
    private LocalDateTime createTime;
}
