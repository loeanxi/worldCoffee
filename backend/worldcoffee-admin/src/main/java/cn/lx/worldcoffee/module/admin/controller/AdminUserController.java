package cn.lx.worldcoffee.module.admin.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.admin.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 *
 * GET  /api/admin/users           — 用户列表（分页 + 关键词搜索）
 * POST /api/admin/users/{id}/freeze   — 冻结用户
 * POST /api/admin/users/{id}/unfreeze — 解冻用户
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminService adminService;

    /** 用户列表（分页 + 关键词搜索用户名/手机号） */
    @GetMapping
    public Result<List<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listUsers(page, size, keyword));
    }

    /** 冻结用户（status 置为 0，该用户无法登录） */
    @PostMapping("/{id}/freeze")
    public Result<Void> freezeUser(@PathVariable("id") Long id) {
        adminService.freezeUser(id);
        return Result.success(null);
    }

    /** 解冻用户（status 置为 1，恢复正常登录） */
    @PostMapping("/{id}/unfreeze")
    public Result<Void> unfreezeUser(@PathVariable("id") Long id) {
        adminService.unfreezeUser(id);
        return Result.success(null);
    }
}
