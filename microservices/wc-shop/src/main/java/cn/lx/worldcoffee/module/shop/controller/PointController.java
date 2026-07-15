package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.shop.domain.PointExchangeRule;
import cn.lx.worldcoffee.module.shop.domain.vo.PointBalanceVO;
import cn.lx.worldcoffee.module.shop.domain.vo.PointRecordVO;
import cn.lx.worldcoffee.module.shop.service.PointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 积分接口
 */
@Tag(name = "积分模块", description = "积分余额、流水、兑换")
@RestController
@RequestMapping("/api/shop/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping("/balance")
    @Operation(summary = "查询积分余额和等级")
    public Result<PointBalanceVO> balance() {
        return Result.success(pointService.getBalance());
    }

    @GetMapping("/records")
    @Operation(summary = "积分流水")
    public Result<List<PointRecordVO>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(pointService.myRecords(page, size));
    }

    @GetMapping("/exchange-rules")
    @Operation(summary = "可兑换列表")
    public Result<List<PointExchangeRule>> exchangeRules() {
        return Result.success(pointService.listExchangeRules());
    }

    @PostMapping("/exchange/{ruleId}")
    @Operation(summary = "积分兑换优惠券")
    public Result<Void> exchange(@PathVariable Long ruleId) {
        pointService.exchangeCoupon(ruleId);
        return Result.success(null);
    }
}
