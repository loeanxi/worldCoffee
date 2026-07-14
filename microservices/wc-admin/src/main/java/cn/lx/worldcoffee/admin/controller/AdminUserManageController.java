package cn.lx.worldcoffee.admin.controller;

import cn.lx.worldcoffee.admin.service.AdminUserManageService;
import cn.lx.worldcoffee.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserManageController {

    private final AdminUserManageService adminUserManageService;

    @GetMapping
    public Result<List<Map<String, Object>>> listUsers(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(required = false) String keyword) {
        return Result.success(adminUserManageService.listUsers(page, size, keyword));
    }

    @PostMapping("/{id}/freeze")
    public Result<Void> freezeUser(@PathVariable Long id) {
        adminUserManageService.updateStatus(id, 0);
        return Result.success(null);
    }

    @PostMapping("/{id}/unfreeze")
    public Result<Void> unfreezeUser(@PathVariable Long id) {
        adminUserManageService.updateStatus(id, 1);
        return Result.success(null);
    }
}
