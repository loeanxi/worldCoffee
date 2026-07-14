package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.shop.domain.admin.AdminLoginForm;
import cn.lx.worldcoffee.module.shop.domain.admin.AdminLoginVO;
import cn.lx.worldcoffee.module.shop.service.AdminShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminShopService adminShopService;

    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginForm form) {
        return Result.success(adminShopService.login(form));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        adminShopService.logout(authHeader);
        return Result.success(null);
    }
}
