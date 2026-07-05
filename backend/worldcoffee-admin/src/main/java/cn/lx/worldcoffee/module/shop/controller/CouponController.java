package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.shop.domain.vo.CouponVO;
import cn.lx.worldcoffee.module.shop.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shop/coupons")
@RequiredArgsConstructor
public class CouponController {
    private final CouponService couponService;

    @Operation(summary = "可领取的优惠券列表")
    @GetMapping
    public Result<List<CouponVO>> listAvailable() {
        return Result.success(couponService.listAvailableCoupons());
    }

    @Operation(summary = "领取优惠券")
    @PostMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id) {
        couponService.claimCoupon(id);
        return Result.success(null);
    }

    @Operation(summary = "我的优惠券")
    @GetMapping("/my")
    public Result<List<CouponVO>> myCoupons() {
        return Result.success(couponService.myCoupons());
    }


}
