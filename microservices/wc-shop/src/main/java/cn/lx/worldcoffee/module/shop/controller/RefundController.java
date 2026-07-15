package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.shop.domain.from.RefundApplyForm;
import cn.lx.worldcoffee.module.shop.domain.vo.RefundVO;
import cn.lx.worldcoffee.module.shop.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 退款接口
 */
@Tag(name = "退款模块", description = "退款/退货申请与处理")
@RestController
@RequestMapping("/api/shop/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    @Operation(summary = "申请退款")
    public Result<RefundVO> applyRefund(@Valid @RequestBody RefundApplyForm form) {
        return Result.success(refundService.applyRefund(form));
    }

    @GetMapping("/my")
    @Operation(summary = "我的退款列表")
    public Result<List<RefundVO>> myRefunds(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(refundService.myRefunds(page, size));
    }

    @GetMapping("/order/{orderNo}")
    @Operation(summary = "按订单号查退款")
    public Result<RefundVO> getByOrderNo(@PathVariable String orderNo) {
        return Result.success(refundService.getByOrderNo(orderNo));
    }

    @PutMapping("/{refundNo}/audit")
    @Operation(summary = "审核退款（admin）")
    public Result<Void> auditRefund(
            @PathVariable String refundNo,
            @RequestParam boolean approved,
            @RequestParam(required = false) String remark) {
        refundService.auditRefund(refundNo, approved, remark);
        return Result.success(null);
    }

    @PutMapping("/{refundNo}/cancel")
    @Operation(summary = "取消退款申请")
    public Result<Void> cancelRefund(@PathVariable String refundNo) {
        refundService.cancelRefund(refundNo);
        return Result.success(null);
    }

    @GetMapping("/admin/list")
    @Operation(summary = "admin退款列表")
    public Result<List<RefundVO>> listRefunds(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(refundService.listRefunds(status, page, size));
    }
}
