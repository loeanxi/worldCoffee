package cn.lx.worldcoffee.common.security;

import cn.lx.worldcoffee.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 微服务架构下的用户工具类。
 * 网关 JWT 过滤器校验 token 后，将 userId/username 放入请求头（X-User-Id / X-Username），
 * 下游服务直接读请求头，不再依赖 Spring Security 的 SecurityContext。
 */
public class SecurityUtils {

    private static final Logger log = LoggerFactory.getLogger(SecurityUtils.class);

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
            // 请求头中没有 X-User-Id，属于正常未登录场景，不打日志
        } catch (NullPointerException | ClassCastException e) {
            // RequestContextHolder 未初始化或不是 Servlet 环境，属于框架层面异常
            log.warn("[SecurityUtils] 获取当前用户ID失败：RequestContextHolder 环境异常", e);
        } catch (NumberFormatException e) {
            // X-User-Id 头部格式非法，可能是网关异常或恶意请求
            log.warn("[SecurityUtils] X-User-Id 头部格式非法: {}", e.getMessage());
        } catch (Exception e) {
            // 其他未预期异常，记录日志但不抛出，避免影响业务
            log.error("[SecurityUtils] 获取当前用户ID时发生未预期异常", e);
        }
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
        } catch (NullPointerException | ClassCastException e) {
            log.warn("[SecurityUtils] 获取当前用户名失败：RequestContextHolder 环境异常", e);
        } catch (Exception e) {
            log.error("[SecurityUtils] 获取当前用户名时发生未预期异常", e);
        }
        return null;
    }
}
