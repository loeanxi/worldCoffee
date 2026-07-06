package cn.lx.worldcoffee.module.admin.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.admin.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 仪表盘控制器
 *
 * GET /api/admin/dashboard — 基础统计数据（用户数、商品数、订单数、销售额等）
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminService adminService;

    /** 仪表盘统计数据 */
    @GetMapping
    public Result<Map<String, Object>> stats() {
        return Result.success(adminService.getDashboardStats());
    }
}
