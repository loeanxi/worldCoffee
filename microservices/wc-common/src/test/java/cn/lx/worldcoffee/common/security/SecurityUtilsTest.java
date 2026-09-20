package cn.lx.worldcoffee.common.security;

import cn.lx.worldcoffee.common.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityUtils 边界测试：
 * 重点区分「正常未登录」（静默返回 null）与「上下文/格式异常」（记日志但仍返回 null 不抛出）。
 */
class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void getCurrentUserId_无请求上下文_返回null不抛异常() {
        // MQ 消费者、定时任务等非 HTTP 线程：RequestContextHolder 未初始化
        assertThat(SecurityUtils.getCurrentUserId()).isNull();
    }

    @Test
    void getCurrentUserId_有请求但无用户头_返回null() {
        // 正常未登录场景：网关未注入 X-User-Id
        bindRequest(new MockHttpServletRequest());
        assertThat(SecurityUtils.getCurrentUserId()).isNull();
    }

    @Test
    void getCurrentUserId_用户头格式非法_返回null不抛异常() {
        // 网关异常或恶意请求：X-User-Id 不是数字，记 warn 日志但不抛出
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "not-a-number");
        bindRequest(request);

        assertThat(SecurityUtils.getCurrentUserId()).isNull();
    }

    @Test
    void getCurrentUserId_正常用户头_返回用户ID() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "100");
        bindRequest(request);

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(100L);
    }

    @Test
    void requireUserId_未登录_抛401() {
        assertThatThrownBy(SecurityUtils::requireUserId)
                .isInstanceOf(ServiceException.class)
                .hasMessage("未登录");
    }

    @Test
    void getCurrentUsername_无请求上下文_返回null不抛异常() {
        assertThat(SecurityUtils.getCurrentUsername()).isNull();
    }

    @Test
    void getCurrentUsername_正常头部_返回用户名() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Username", "loean");
        bindRequest(request);

        assertThat(SecurityUtils.getCurrentUsername()).isEqualTo("loean");
    }
}
