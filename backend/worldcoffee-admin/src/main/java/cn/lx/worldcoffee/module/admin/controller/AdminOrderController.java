package cn.lx.worldcoffee.module.admin.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.admin.service.AdminService;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单管理控制器
 *
 * GET  /api/admin/orders              — 全量订单列表（支持按状态/用户/订单号筛选）
 * GET  /api/admin/orders/{id}         — 订单详情（含订单项）
 * POST /api/admin/orders/{id}/ship    — 发货（填入快递公司 + 快递单号）
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminService adminService;

    /**
     * 全量订单列表（管理员视角，不按 userId 过滤）
     * status: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消
     * userId: 按用户ID筛选
     * orderNo: 按订单号精确查询
     */
    @GetMapping
    public Result<List<OrderVO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String orderNo) {
        return Result.success(adminService.listAllOrders(page, size, status, userId, orderNo));
    }

    /** 订单详情（含订单项） */
    @GetMapping("/{id}")
    public Result<OrderVO> getOrder(@PathVariable Long id) {
        return Result.success(adminService.getOrderDetail(id));
    }

    /**
     * 发货
     * 只有已支付（status=1）的订单才能发货
     * 发货后状态变为 2（已发货），同时生成 mock 物流记录
     */
    @PostMapping("/{id}/ship")
    public Result<Void> shipOrder(
            @PathVariable Long id,
            @RequestParam String shippingCompany,
            @RequestParam String trackingNo) {
        adminService.shipOrder(id, shippingCompany, trackingNo);
        return Result.success(null);
    }
}
