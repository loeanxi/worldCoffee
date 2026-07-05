package cn.lx.worldcoffee.module.coffee.domain;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_follow")
public class UserFollow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long followerId;    // 关注者 = 当前登录用户
    private Long followeeId;    // 被关注者 = URL上的用户ID
    private LocalDateTime createTime;
}
//POST /api/users/5/follow    ← 关注 id=5 的用户，我是当前登录用户（id=3）
//
//查有没有关注过：SELECT COUNT(*) FROM user_follow WHERE follower_id = 3 AND followee_id = 5
//
//  ├─ count = 0（没关注）
//  │      INSERT INTO user_follow (follower_id, followee_id) VALUES (3, 5)
//  │      返回 true（关注成功）
//  │
//  └─ count = 1（已关注）
//         DELETE FROM user_follow WHERE follower_id = 3 AND followee_id = 5
//         返回 false（取消关注）