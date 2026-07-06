package cn.lx.worldcoffee.module.admin.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.admin.domain.AdminLoginForm;
import cn.lx.worldcoffee.module.admin.domain.AdminLoginVO;
import cn.lx.worldcoffee.module.admin.service.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员认证控制器
 *
 * POST /api/admin/login  — 管理员登录，返回 JWT（role=ADMIN）
 * POST /api/admin/logout — 管理员登出，token 加入黑名单
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /** 管理员登录 */
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginForm form) {
        AdminLoginVO vo = adminAuthService.login(form);
        return Result.success(vo);
    }

    /** 管理员登出 */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        adminAuthService.logout(authHeader);
        return Result.success(null);
    }
}
