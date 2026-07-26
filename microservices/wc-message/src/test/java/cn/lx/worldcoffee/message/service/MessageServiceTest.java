package cn.lx.worldcoffee.message.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.message.dao.MessageDao;
import cn.lx.worldcoffee.message.domain.PrivateMessage;
import cn.lx.worldcoffee.message.domain.from.SendMessageForm;
import cn.lx.worldcoffee.message.domain.vo.MessageVO;
import cn.lx.worldcoffee.message.feign.UserFeignClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageDao messageDao;
    @Mock private UserFeignClient userFeignClient;
    @Mock private RabbitTemplate rabbitTemplate;
    @InjectMocks private MessageService messageService;

    @Test
    void sendMessage_正常发送() {
        Long fromId = 100L;
        SendMessageForm form = new SendMessageForm();
        form.setToId(200L);
        form.setContent("你好");

        UserFeignClient.UserInfo fromUser = new UserFeignClient.UserInfo(100L, "张三", "/avatar/1.png");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(fromId);
            when(userFeignClient.batchGetUsers(List.of(fromId))).thenReturn(Map.of(fromId, fromUser));

            MessageVO result = messageService.sendMessage(form);

            assertThat(result).isNotNull();
            assertThat(result.getFromId()).isEqualTo(fromId);
            assertThat(result.getToId()).isEqualTo(200L);
            assertThat(result.getContent()).isEqualTo("你好");
            assertThat(result.getIsRead()).isFalse();

            // 验证 DB 写入
            verify(messageDao).insert(any(PrivateMessage.class));
            // 验证 MQ 发送（消息格式包含 fromId|||toId|||content）
            verify(rabbitTemplate).convertAndSend(eq("chat.exchange"), eq("chat.200"), contains("100|||200|||"));
        }
    }

    @Test
    void sendMessage_给自己发_抛异常() {
        SendMessageForm form = new SendMessageForm();
        form.setToId(100L);
        form.setContent("自言自语");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(100L);

            assertThatThrownBy(() -> messageService.sendMessage(form))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("不能给自己发消息");

            verify(messageDao, never()).insert(any(PrivateMessage.class));
            verify(rabbitTemplate, never()).convertAndSend(any(), any(), (Object) any());
        }
    }

    @Test
    void getUnreadCount_有未读_返回正确数量() {
        Long userId = 100L;

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(messageDao.selectCount(any())).thenReturn(5L);

            Long count = messageService.getUnreadCount();

            assertThat(count).isEqualTo(5L);
        }
    }

    @Test
    void getUnreadCount_未登录_返回0() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(null);

            Long count = messageService.getUnreadCount();

            assertThat(count).isEqualTo(0L);
            verify(messageDao, never()).selectCount(any());
        }
    }

    @Test
    void markAsRead_正常标记() {
        Long userId = 100L;

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);

            messageService.markAsRead(200L);

            // 验证 update 被调用（fromId=200, toId=100, isRead 0→1）
            verify(messageDao).update(any(), any());
        }
    }

    @Test
    void listSessions_有多个会话_验证unreadCount计算() {
        Long userId = 100L;

        PrivateMessage m1 = new PrivateMessage();
        m1.setFromId(200L);
        m1.setToId(100L);
        m1.setContent("hi");
        m1.setIsRead(0);  // 未读

        PrivateMessage m2 = new PrivateMessage();
        m2.setFromId(100L);
        m2.setToId(200L);
        m2.setContent("hello");
        m2.setIsRead(1);  // 已读（自己发的）

        PrivateMessage m3 = new PrivateMessage();
        m3.setFromId(300L);
        m3.setToId(100L);
        m3.setContent("在吗");
        m3.setIsRead(0);  // 未读

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(messageDao.selectList(any())).thenReturn(List.of(m1, m2, m3));
            when(userFeignClient.batchGetUsers(any())).thenReturn(Map.of());

            var sessions = messageService.listSessions();

            assertThat(sessions).hasSize(2);  // 两个会话：200和300

            // 会话200有1条未读（m1）
            var session200 = sessions.stream().filter(s -> s.getUserId() == 200L).findFirst().orElseThrow();
            assertThat(session200.getUnreadCount()).isEqualTo(1);

            // 会话300有1条未读（m3）
            var session300 = sessions.stream().filter(s -> s.getUserId() == 300L).findFirst().orElseThrow();
            assertThat(session300.getUnreadCount()).isEqualTo(1);
        }
    }
}
