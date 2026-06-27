package cn.lx.worldcoffee.module.notification.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    /** 通知ID，主键自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收通知的用户ID */
    private Long receiverId;

    /** 触发通知的用户ID */
    private Long senderId;

    /** 通知类型：LIKE-点赞, COMMENT-评论, FOLLOW-关注, FAVORITE-收藏 */
    private String type;

    /** 关联的帖子ID */
    private Long postId;

    /** 关联的评论ID */
    private Long commentId;

    /** 通知内容摘要 */
    private String content;

    /** 是否已读：0-未读, 1-已读 */
    private Integer isRead;

    /** 通知创建时间 */
    private LocalDateTime createTime;
}




