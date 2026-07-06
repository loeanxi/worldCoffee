package cn.lx.worldcoffee.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类。
 *
 * 为什么抽出来：
 *   原来 ShopService、ShopController 各自写了一遍 getCurrentUserId()，
 *   逻辑完全一样——从 Spring Security 的 SecurityContext 里拿当前登录用户的 ID。
 *   拆分成多个 Service 之后，每个 Service 都要拿 userId，
 *   所以统一放这里，所有地方调 SecurityUtils.requireUserId() 就行。
 *
 * 两个方法：
 *   getCurrentUserId()  —— 可能返回 null（没登录也允许的场景，比如公开页面）
 *   requireUserId()     —— 没登录直接抛异常（需要登录才能调的接口用这个）
 */
public final class SecurityUtils {

    private SecurityUtils() {} // 工具类，不让 new

    /**
     * 拿当前登录用户 ID，没登录返回 null。
     * 适用于"登录了就用登录身份，没登录也行"的场景。
     */
    public static Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return Long.valueOf(auth.getPrincipal().toString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 拿当前登录用户 ID，没登录直接抛"请先登录"。
     * 适用于"必须登录才能操作"的接口（下单、购物车、地址等）。
     */
    public static Long requireUserId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new cn.lx.worldcoffee.common.exception.ServiceException("请先登录");
        }
        return userId;
    }
}
