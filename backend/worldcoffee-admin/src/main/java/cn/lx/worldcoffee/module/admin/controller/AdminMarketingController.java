package cn.lx.worldcoffee.module.admin.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.admin.service.AdminService;
import cn.lx.worldcoffee.module.shop.domain.Coupon;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 营销管理控制器（优惠券 + 秒杀活动）
 *
 * GET    /api/admin/marketing/coupons              — 优惠券列表（type 可选：1-满减 2-折扣 3-秒杀）
 * POST   /api/admin/marketing/coupons              — 创建优惠券
 * PUT    /api/admin/marketing/coupons/{id}          — 编辑优惠券
 * DELETE /api/admin/marketing/coupons/{id}          — 删除优惠券
 * POST   /api/admin/marketing/coupons/{id}/toggle   — 切换上下架
 * GET    /api/admin/marketing/coupons/{id}/products — 查看秒杀券关联商品
 * PUT    /api/admin/marketing/coupons/{id}/products — 设置秒杀券关联商品
 * GET    /api/admin/marketing/coupons/{id}/participants — 查看领取记录
 */
@RestController
@RequestMapping("/api/admin/marketing")
@RequiredArgsConstructor
public class AdminMarketingController {

    private final AdminService adminService;

    /**
     * 优惠券列表
     * type 可选：1-满减券 2-折扣券 3-秒杀券，不传则查全部
     */
    @GetMapping("/coupons")
    public Result<List<Map<String, Object>>> listCoupons(
            @RequestParam(required = false) Integer type) {
        return Result.success(adminService.listCoupons(type));
    }

    /** 创建优惠券 */
    @PostMapping("/coupons")
    public Result<Coupon> createCoupon(@RequestBody Coupon coupon) {
        return Result.success(adminService.createCoupon(coupon));
    }

    /** 编辑优惠券 */
    @PutMapping("/coupons/{id}")
    public Result<Void> updateCoupon(@PathVariable Long id, @RequestBody Coupon coupon) {
        adminService.updateCoupon(id, coupon);
        return Result.success(null);
    }

    /** 删除优惠券（同时清理关联的 coupon_product） */
    @DeleteMapping("/coupons/{id}")
    public Result<Void> deleteCoupon(@PathVariable Long id) {
        adminService.deleteCoupon(id);
        return Result.success(null);
    }

    /** 切换优惠券上下架状态 */
    @PostMapping("/coupons/{id}/toggle")
    public Result<Void> toggleCouponStatus(@PathVariable Long id) {
        adminService.toggleCouponStatus(id);
        return Result.success(null);
    }

    /** 查看秒杀券关联的商品ID列表 */
    @GetMapping("/coupons/{id}/products")
    public Result<List<Long>> getCouponProducts(@PathVariable Long id) {
        return Result.success(adminService.getCouponProductIds(id));
    }

    /** 设置秒杀券关联的商品（覆盖式：先删旧关联，再插新关联） */
    @PutMapping("/coupons/{id}/products")
    public Result<Void> setCouponProducts(
            @PathVariable Long id,
            @RequestBody List<Long> productIds) {
        adminService.setCouponProducts(id, productIds);
        return Result.success(null);
    }

    /** 查看某优惠券的领取记录（哪些用户领了、是否已使用） */
    @GetMapping("/coupons/{id}/participants")
    public Result<List<Map<String, Object>>> getParticipants(@PathVariable Long id) {
        return Result.success(adminService.getCouponParticipants(id));
    }
}
