package cn.lx.worldcoffee.admin.controller;

import cn.lx.worldcoffee.admin.service.AdminAuthService;
import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.shop.domain.admin.AdminLoginForm;
import cn.lx.worldcoffee.module.shop.domain.admin.AdminLoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginForm form) {
        return Result.success(adminAuthService.login(form));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        adminAuthService.logout(authHeader);
        return Result.success(null);
    }
}
