package cn.lx.worldcoffee.module.notification.service;

import cn.lx.worldcoffee.module.notification.domain.NotificationEvent;
import cn.lx.worldcoffee.module.notification.domain.vo.NotificationVO;

import java.util.List;

public interface NotificationService {
    /** 发送通知：落库 + Redis 实时推送 */
    void send(NotificationEvent event);

    /**
     * 通知列表（分页 + 过滤）
     * @param unreadOnly true=只看未读, false=全部
     */

    List<NotificationVO> listNotifications(Long userId, boolean unreadOnly, int page, int size);

    /** 未读数量（用于红点 badge） */
    long countUnread(Long userId);

    /** 单条标记已读 */
    void markAsRead(Long notificationId);

    /** 一键全部已读 */
    void markAllAsRead(Long userId);

    void deleteNotification(Long notificationId);

}
