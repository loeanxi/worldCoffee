package cn.lx.worldcoffee.common.security;

import cn.lx.worldcoffee.common.exception.ServiceException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 微服务架构下的用户工具类。
 * 网关 JWT 过滤器校验 token 后，将 userId/username 放入请求头（X-User-Id / X-Username），
 * 下游服务直接读请求头，不再依赖 Spring Security 的 SecurityContext。
 */
public class SecurityUtils {

    /**
     * 获取当前用户ID（可选），没有登录返回 null
     */
    public static Long getCurrentUserId() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes()).getRequest();
            String userIdStr = request.getHeader("X-User-Id");
            if (userIdStr != null && !userIdStr.isEmpty()) {
                return Long.valueOf(userIdStr);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 获取当前用户ID（必须），未登录抛 401
     */
    public static Long requireUserId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(401, "未登录");
        }
        return userId;
    }

    /**
     * 获取当前用户名（可选），没有返回 null
     */
    public static String getCurrentUsername() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes()).getRequest();
            return request.getHeader("X-Username");
        } catch (Exception ignored) {}
        return null;
    }
}
