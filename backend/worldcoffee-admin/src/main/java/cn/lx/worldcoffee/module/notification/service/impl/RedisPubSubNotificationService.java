package cn.lx.worldcoffee.module.notification.service.impl;

import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.notification.dao.NotificationDao;
import cn.lx.worldcoffee.module.notification.domain.Notification;
import cn.lx.worldcoffee.module.notification.domain.NotificationEvent;
import cn.lx.worldcoffee.module.notification.domain.vo.NotificationVO;
import cn.lx.worldcoffee.module.notification.service.NotificationService;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "notification.provider", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
public class RedisPubSubNotificationService implements NotificationService {

    private final NotificationDao notificationDao;
    private final StringRedisTemplate redisTemplate;
    private final UserDao userDao;

    /** 获取当前登录用户ID，未登录返回null */

    @Override
    public void send(NotificationEvent event) {
        // 1. 落盘（MySQL，用于查询历史通知）
        Notification notification = new Notification();
        notification.setReceiverId(event.getReceiverId());
        notification.setSenderId(event.getSenderId());
        notification.setType(event.getType());
        notification.setPostId(event.getPostId());
        notification.setCommentId(event.getCommentId());
        notification.setContent(event.getContent());
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        // SQL: INSERT INTO notification (receiver_id, sender_id, ...) VALUES (?, ?, ...)
        notificationDao.insert(notification);

        // 2. 实时推送（Redis Pub/Sub → SSE → 前端）
        // 不需要序列化完整对象，前端只需要知道"有新通知"即可
        //// 等价于 Redis 命令：PUBLISH "notify:3" "LIKE"
        //                             ↑频道名       ↑消息内容
        redisTemplate.convertAndSend("notify:" + event.getReceiverId(), event.getType());
        //这一步做了两件事：通知落库（持久化）+ 发 Redis 消息（实时推送）。
    }

    @Override
    public List<NotificationVO> listNotifications(Long userId, boolean unreadOnly, int page, int size) {
        // 1. 构建查询条件
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getReceiverId,userId);
        if (unreadOnly){
            wrapper.eq(Notification::getIsRead,0);//只查未读
        }
        wrapper.orderByDesc(Notification::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);
        // SQL: SELECT * FROM notification WHERE receiver_id = ? AND is_read = 0
        //      ORDER BY create_time DESC LIMIT 0,20

        List<Notification> list = notificationDao.selectList(wrapper);
        if (list.isEmpty()) return List.of();

        // 2. 批量查发送者 —— 修 N+1
        // 收集所有 senderId → 去重 → 一次 IN 查询
        // SQL: SELECT * FROM sys_user WHERE id IN (?,?,?)
        Map<Long, User> userMap = userDao.selectBatchIds(
                        list.stream().map(Notification::getSenderId)
                                .distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        // 3. 组装 VO
        return list.stream().map(n ->{
            User sender = userMap.get(n.getSenderId());
            return NotificationVO.builder()
                    .id(n.getId())
                    .senderName(sender != null ? sender.getUsername() : "未知")
                    .avatar(sender != null ? sender.getAvatar() : null)
                    .type(n.getType())
                    .content(n.getContent())
                    .postId(n.getPostId())
                    .isRead(n.getIsRead() == 1)
                    .createTime(n.getCreateTime())
                    .senderId(n.getSenderId())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public long countUnread(Long userId) {
        // SQL: SELECT COUNT(*) FROM notification
        // WHERE receiver_id = ? AND is_read = 0
        return notificationDao.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId,userId)
                .eq(Notification::getIsRead,0)
        );
    }


    @Override
    public void markAsRead(Long notificationId) {
        // SQL: UPDATE notification SET is_read = 1 WHERE id = ?
        Notification n = new Notification();
        n.setId(notificationId);
        n.setIsRead(1);
        notificationDao.updateById(n);

    }

    @Override
    public void markAllAsRead(Long userId) {
        // LambdaUpdateWrapper：set 要更新的字段，eq 是 WHERE 条件
        // SQL: UPDATE notification SET is_read = 1
        // WHERE receiver_id = ? AND is_read = 0
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(Notification::getIsRead,1)
                .eq(Notification::getReceiverId,userId)
                .eq(Notification::getIsRead,0);
        notificationDao.update(null,wrapper);
        //LambdaUpdateWrapper：和 LambdaQueryWrapper 是兄弟，
        // 前者负责 UPDATE 的 SET + WHERE，后者负责 SELECT 的 WHERE。
        // MyBatis-Plus 里更新用 set().eq() 链式写法，不用手写 SQL。
    }

    @Override
    public void deleteNotification(Long notificationId) {
        // SQL: SELECT * FROM notification WHERE id = ?
        Notification n = notificationDao.selectById(notificationId);
        if (n == null || !n.getReceiverId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ServiceException("无权操作");
        }
        // SQL: DELETE FROM notification WHERE id = ?
        notificationDao.deleteById(notificationId);
    }

    /**区别在于更新的粒度不同：俩者入参选取的不同
     * markAsRead(notificationId=88)
     *     ↓
     * UPDATE notification SET is_read = 1 WHERE id = 88   ← 精确到"这一条"
     *
     * markAllAsRead(userId=3)
     *     ↓
     * UPDATE notification SET is_read = 1 WHERE receiver_id = 3 AND is_read = 0  ← 批量更新"这个人所有未读"
     *
     * 为什么传参不一样：
     *
     * 单条已读：前端点哪条传哪条的 ID，所以用 notificationId。updateById 会根据实体 ID 自动生成 WHERE id = ?。
     * 一键已读：用户点"全部已读"时不关心具体哪些通知，只知道"我是谁"，所以传 userId。
     * 用 LambdaUpdateWrapper 拼 WHERE receiver_id = ? AND is_read = 0。
     *
     *
     *
     * 为什么 markAllAsRead 不传实体而传 null：
     * // updateById 模式：SET 值从实体拿，WHERE 条件 = 实体的 id
     * notificationDao.updateById(n);  // n 里有 id=88, isRead=1
     *
     * // LambdaUpdateWrapper 模式：SET 值从 wrapper.set() 拿，实体传 null
     * notificationDao.update(null, wrapper);  // null 因为不需要从实体取 SET 值
     *
     *markAllAsRead 没有"要更新的实体"，所有要更新的字段都写在了 wrapper.set(...) 里，所以第一个参数传 null。
     * MyBatis-Plus 遇到 null 实体 + 有 wrapper 时，就用 wrapper 里的 SET 值。
     */
}
