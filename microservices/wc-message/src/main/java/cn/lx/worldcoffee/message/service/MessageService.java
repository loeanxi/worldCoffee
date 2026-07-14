package cn.lx.worldcoffee.message.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.message.dao.MessageDao;
import cn.lx.worldcoffee.message.domain.PrivateMessage;
import cn.lx.worldcoffee.message.domain.vo.MessageVO;
import cn.lx.worldcoffee.message.domain.vo.SessionVO;
import cn.lx.worldcoffee.message.feign.UserFeignClient;
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
    private final UserFeignClient userFeignClient;
    private final RabbitTemplate rabbitTemplate;

    public MessageVO sendMessage(Long toId, String content, Integer messageType) {
        Long fromId = SecurityUtils.requireUserId();

        if (fromId.equals(toId)) throw new ServiceException("不能给自己发消息");

        // 存数据库
        PrivateMessage msg = new PrivateMessage();
        msg.setFromId(fromId);
        msg.setToId(toId);
        msg.setContent(content);
        msg.setMessageType(messageType != null ? messageType : 1);
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        messageDao.insert(msg);

        // 发送到 RabbitMQ
        String routingKey = "chat." + toId;
        String mqMessage = fromId + "|||" + content;
        rabbitTemplate.convertAndSend("chat.exchange", routingKey, mqMessage);

        // 组装 VO
        Map<Long, UserFeignClient.UserInfo> userMap = userFeignClient.batchGetUsers(List.of(fromId));
        UserFeignClient.UserInfo fromUser = userMap.get(fromId);
        
        return MessageVO.builder()
                .id(msg.getId())
                .fromId(fromId)
                .fromName(fromUser != null ? fromUser.username() : "未知")
                .fromAvatar(fromUser != null ? fromUser.avatar() : null)
                .toId(toId)
                .content(content)
                .messageType(msg.getMessageType())
                .isRead(false)
                .createTime(msg.getCreateTime())
                .build();
    }

    public List<MessageVO> getChatHistory(Long otherUserId, int page, int size) {
        Long userId = SecurityUtils.requireUserId();

        List<PrivateMessage> messages = messageDao.selectList(new LambdaQueryWrapper<PrivateMessage>()
                .and(w -> w.eq(PrivateMessage::getFromId, userId)
                        .eq(PrivateMessage::getToId, otherUserId))
                .or(w -> w.eq(PrivateMessage::getFromId, otherUserId)
                        .eq(PrivateMessage::getToId, userId))
                .orderByDesc(PrivateMessage::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size)
        );

        if (messages.isEmpty()) return List.of();

        // 批量查用户信息
        List<Long> userIds = messages.stream()
                .map(PrivateMessage::getFromId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserFeignClient.UserInfo> userMap = userFeignClient.batchGetUsers(userIds);

        return messages.stream().map(msg -> {
            UserFeignClient.UserInfo fromUser = userMap.get(msg.getFromId());
            return MessageVO.builder()
                    .id(msg.getId())
                    .fromId(msg.getFromId())
                    .fromName(fromUser != null ? fromUser.username() : "未知")
                    .fromAvatar(fromUser != null ? fromUser.avatar() : null)
                    .toId(msg.getToId())
                    .content(msg.getContent())
                    .messageType(msg.getMessageType())
                    .isRead(msg.getIsRead() == 1)
                    .createTime(msg.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    public List<SessionVO> listSessions() {
        Long userId = SecurityUtils.requireUserId();

        List<PrivateMessage> allMessages = messageDao.selectList(
                new LambdaQueryWrapper<PrivateMessage>()
                        .eq(PrivateMessage::getFromId, userId)
                        .or()
                        .eq(PrivateMessage::getToId, userId)
                        .orderByDesc(PrivateMessage::getCreateTime)
        );

        if (allMessages.isEmpty()) return List.of();

        // 按对方ID分组
        Map<Long, List<PrivateMessage>> sessionMap = new LinkedHashMap<>();
        for (PrivateMessage msg : allMessages) {
            Long otherId = msg.getFromId().equals(userId) ? msg.getToId() : msg.getFromId();
            sessionMap.computeIfAbsent(otherId, k -> new ArrayList<>());
            sessionMap.get(otherId).add(msg);
        }

        // 批量查用户信息
        Map<Long, UserFeignClient.UserInfo> userMap = userFeignClient.batchGetUsers(
                new ArrayList<>(sessionMap.keySet()));

        // 组装会话 VO
        List<SessionVO> sessions = new ArrayList<>();
        for (Map.Entry<Long, List<PrivateMessage>> entry : sessionMap.entrySet()) {
            Long otherId = entry.getKey();
            List<PrivateMessage> msgs = entry.getValue();
            UserFeignClient.UserInfo otherUser = userMap.get(otherId);

            PrivateMessage lastMsg = msgs.get(0);
            long unreadCount = msgs.stream()
                    .filter(m -> m.getToId().equals(userId) && m.getIsRead() == 0)
                    .count();

            sessions.add(SessionVO.builder()
                    .userId(otherId)
                    .username(otherUser != null ? otherUser.username() : "未知")
                    .avatar(otherUser != null ? otherUser.avatar() : null)
                    .lastMessage(lastMsg.getContent())
                    .lastTime(lastMsg.getCreateTime())
                    .unreadCount(unreadCount)
                    .build());
        }
        return sessions;
    }

    public void markAsRead(Long otherUserId) {
        Long userId = SecurityUtils.requireUserId();

        messageDao.update(null, new LambdaUpdateWrapper<PrivateMessage>()
                .eq(PrivateMessage::getFromId, otherUserId)
                .eq(PrivateMessage::getToId, userId)
                .eq(PrivateMessage::getIsRead, 0)
                .set(PrivateMessage::getIsRead, 1));
    }

    public Long getUnreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return 0L;

        return messageDao.selectCount(
                new LambdaQueryWrapper<PrivateMessage>()
                        .eq(PrivateMessage::getToId, userId)
                        .eq(PrivateMessage::getIsRead, 0)
        );
    }
}
