package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.shop.domain.from.ReviewForm;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductReviewSummaryVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ReviewVO;
import cn.lx.worldcoffee.module.shop.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品评价接口
 */
@Tag(name = "商品评价模块", description = "订单完成后商品评价")
@RestController
@RequestMapping("/api/shop/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @Operation(summary = "提交评价")
    public Result<Void> submitReview(@Valid @RequestBody ReviewForm form) {
        reviewService.submitReview(form);
        return Result.success(null);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "商品评价列表")
    public Result<List<ReviewVO>> productReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(reviewService.listProductReviews(productId, page, size));
    }

    @GetMapping("/product/{productId}/summary")
    @Operation(summary = "商品评分聚合")
    public Result<ProductReviewSummaryVO> productReviewSummary(@PathVariable Long productId) {
        return Result.success(reviewService.getProductReviewSummary(productId));
    }

    @GetMapping("/my")
    @Operation(summary = "我的评价列表")
    public Result<List<ReviewVO>> myReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(reviewService.myReviews(page, size));
    }

    @PutMapping("/{reviewId}/reply")
    @Operation(summary = "商家回复评价")
    public Result<Void> replyReview(
            @PathVariable Long reviewId,
            @RequestParam String reply) {
        reviewService.replyReview(reviewId, reply);
        return Result.success(null);
    }
}
