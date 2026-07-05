package cn.lx.worldcoffee.module.message.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.message.dao.MessageDao;
import cn.lx.worldcoffee.module.message.domain.PrivateMessage;
import cn.lx.worldcoffee.module.message.domain.vo.MessageVO;
import cn.lx.worldcoffee.module.message.domain.vo.SessionVO;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageDao messageDao;
    private final UserDao userDao;
    private final RabbitTemplate rabbitTemplate;

    /** 从 Spring Security 拿当前登录用户ID */
    private Long getCurrentUserId() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return Long.valueOf(auth.getPrincipal().toString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendMessage(Long toId, String content, Integer messageType) {
        // ─── 1. 校验当前用户是否登录 ───
        Long fromId = getCurrentUserId();
        if (fromId == null) throw new ServiceException("请先登录");

        // ─── 2. 不能给自己发消息 ───
        if (fromId.equals(toId)) throw new ServiceException("不能给自己发消息");

        // ─── 3. 查收信人是否存在 ───
        User toUser = userDao.selectById(toId);
        if (toUser == null) throw new ServiceException("用户不存在");

        // ─── 4. 存数据库 ───
        PrivateMessage msg = new PrivateMessage();
        msg.setFromId(fromId);
        msg.setToId(toId);
        msg.setContent(content);
        msg.setMessageType(messageType != null ? messageType : 1);
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        messageDao.insert(msg);

        // ─── 5. 发到 RabbitMQ，让收信人实时收到 ───
        String routingKey = "chat." + toId;
        String mqMessage = fromId + "|||" + content;
        rabbitTemplate.convertAndSend("chat.exchange",routingKey,mqMessage);

        // ─── 6. 组装 VO 返回 ───
        User fromUser = userDao.selectById(fromId);
        return MessageVO.builder()
                .id(msg.getId())
                .fromId(fromId)
                .fromName(fromUser != null ? fromUser.getUsername() : "未知")
                .fromAvatar(fromUser != null ? fromUser.getAvatar() : null)
                .toId(toId)
                .content(content)
                .messageType(msg.getMessageType())
                .isRead(false)
                .createTime(msg.getCreateTime())
                .build();
    }

    public List<MessageVO> getChatHistory(Long otherUserId, int page, int size) {
        // ─── 1. 校验当前用户是否登录 ───
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // ─── 2. 查两人之间的所有消息 ───
        // 条件：(fromId=我 AND toId=对方) OR (fromId=对方 AND toId=我)
        // 这样才能把"我发的"和"对方发的"都查出来
        /**
         * SELECT * FROM private_message
         * WHERE (from_id = 3 AND to_id = 5)   -- 我发给对方的
         *    OR (from_id = 5 AND to_id = 3)   -- 对方发给我的
         * ORDER BY create_time DESC
         * LIMIT 0, 20
         */
        List<PrivateMessage> messages = messageDao.selectList(new LambdaQueryWrapper<PrivateMessage>()
                .and(w -> w.eq(PrivateMessage::getFromId, userId)
                        .eq(PrivateMessage::getToId, otherUserId))
                .or(w -> w.eq(PrivateMessage::getFromId, otherUserId)
                        .eq(PrivateMessage::getToId, userId))
                .orderByDesc(PrivateMessage::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size)
        );

        if (messages.isEmpty()) return List.of();

        // ─── 3. 收集所有出现过的用户ID，批量查用户信息 ───
        // 一条消息里 fromId 可能是我也可能是对方，都需要查用户名和头像
        List<Long> userIds = messages.stream()
                .map(PrivateMessage::getFromId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userDao.selectBatchIds(userIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        // ─── 4. 组装 VO ───
        //就是把一个 List<PrivateMessage>（数据库查出来的原始数据）转成 List<MessageVO>（前端需要的格式）
        return messages.stream().map(msg -> {
            User fromUser = userMap.get(msg.getFromId());
            return MessageVO.builder()
                    .id(msg.getId())
                    .fromId(msg.getFromId())
                    .fromName(fromUser != null ? fromUser.getUsername() : "未知")
                    .fromAvatar(fromUser != null ? fromUser.getAvatar() : null)
                    .toId(msg.getToId())
                    .content(msg.getContent())
                    .messageType(msg.getMessageType())
                    .isRead(msg.getIsRead() == 1)
                    .createTime(msg.getCreateTime())
                    .build();
        }).collect(Collectors.toList());

        /**为什么只需要fromAvatar就可以有双方的头像
         * 拆成一条行记录来看
         * ┌────────────────────────┐
         * │ [张三头像] 你好         │  ← 张三发的，显示张三头像
         * │                          │
         * │         在吗 [李四头像]  │  ← 李四发的，显示李四头像
         * │                          │
         * │ [张三头像] 吃了          │  ← 张三发的，显示张三头像
         * └────────────────────────┘
         * 这条消息跟我有关，但对方是谁取决于这条消息的方向：
         *
         * 消息方向	fromId	toId	对方是谁
         * 我发给张三	我(3)	张三(5)	toId = 5（张三）
         * 张三发给我	张三(5)	我(3)	fromId = 5（张三）
         * 所以不能固定取 fromId 或 toId，要根据消息方向判断。
         *
         * 敲进去，下一步写最后的 markAsRead 和 getUnreadCount。
         */


    }

    /**
     * ┌──────────────────────────────┐
     * │ [张三头像]  张三    2条未读  │  ← 张三给我发了2条没看的
     * │             昨天 你好        │
     * ├──────────────────────────────┤
     * │ [李四头像]  李四            │  ← 跟李四的聊天
     * │             已读 在吗        │
     * └──────────────────────────────┘
     * 每一行 = 一个会话 = 跟一个人的聊天摘要。
     */
    public List<SessionVO> listSessions() {
        // ─── 1. 校验 ───
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // ─── 2. 查所有跟我有关的消息，按时间倒序 ───
        List<PrivateMessage> allMessages = messageDao.selectList(
                new LambdaQueryWrapper<PrivateMessage>()
                        .eq(PrivateMessage::getFromId, userId)
                        .or()
                        .eq(PrivateMessage::getToId, userId)
                        .orderByDesc(PrivateMessage::getCreateTime)
        );

        if (allMessages.isEmpty()) return List.of();

        // ─── 3. 按"跟谁聊"分组 ───
        //     key = 对方的ID，value = 跟这个人有关的消息列表
        Map<Long, List<PrivateMessage>> sessionMap = new LinkedHashMap<>();
        /**
         * {
         *   3: [消息1, 消息2, 消息3],     ← 跟张三的聊天记录
         *   5: [消息4, 消息5],             ← 跟李四的聊天记录
         *   7: [消息6]                      ← 跟王五的聊天记录
         * }
         */

        for (PrivateMessage msg : allMessages) {
            // 对方的ID：如果消息是我发的，对方是 toId；如果消息是对方发的，对方是 fromId
            Long otherId = msg.getFromId().equals(userId) ? msg.getToId() : msg.getFromId();

            // 如果这个会话还没建，建一个空列表
            sessionMap.computeIfAbsent(otherId,k -> new ArrayList<>());
            // 把消息丢进这个会话里
            sessionMap.get(otherId).add(msg);
        }

        // ─── 4. 批量查所有会话对象的用户信息 ───
        Map<Long, User> userMap = userDao.selectBatchIds(sessionMap.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // ─── 5. 组装每个会话的 VO ───
        List<SessionVO> sessions = new ArrayList<>();
        /**
         * Map                            entrySet()
         * ────                            ─────────
         * {                                [
         *   3: [消息1, 消息2, 消息3],        Entry(3, [消息1, 消息2, 消息3]),
         *   5: [消息4, 消息5],               Entry(5, [消息4, 消息5]),
         *   7: [消息6]                       Entry(7, [消息6])
         * }                                ]
         * Map.Entry<Long, List<PrivateMessage>> 就是这一对东西的类型：
         */

        for (Map.Entry<Long, List<PrivateMessage>> entry : sessionMap.entrySet()){
            Long otherId = entry.getKey();
            List<PrivateMessage> msgs = entry.getValue();
            User otherUser = userMap.get(otherId);

            // 第一条消息（时间最新的）就是"最后一条消息"
            PrivateMessage lastMsg = msgs.get(0);

            // 计算未读数：对方发给我的，且 isRead = 0
            long unreadCount = msgs.stream().filter(m -> m.getToId()
                            .equals(userId) && m.getIsRead() == 0)
                    .count();

            sessions.add(SessionVO.builder()
                            .userId(otherId)
                            .username(otherUser != null ? otherUser.getUsername() : "未知")
                            .avatar(otherUser != null ? otherUser.getAvatar() : null)
                            .lastMessage(lastMsg.getContent())
                            .lastTime(lastMsg.getCreateTime())
                            .unreadCount(unreadCount)
                            .build());
        }
        return sessions;
    }

    public void markAsRead(Long otherUserId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // 把对方发给我的未读消息全部标为已读
        messageDao.update(null,new LambdaUpdateWrapper<PrivateMessage>()
                .eq(PrivateMessage::getFromId,otherUserId) //对方发的
                .eq(PrivateMessage::getToId,userId) //发给我的
                .eq(PrivateMessage::getIsRead,0) //还未读的
                .set(PrivateMessage::getIsRead,1) //改成已读
        );
    }


    public Long getUnreadCount() {
        Long userId = getCurrentUserId();
        if (userId == null) return 0L;

        return messageDao.selectCount(
                new LambdaQueryWrapper<PrivateMessage>()
                        .eq(PrivateMessage::getToId, userId)
                        .eq(PrivateMessage::getIsRead, 0)
        );
    }
}