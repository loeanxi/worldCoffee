package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.shop.domain.from.AddCartFrom;
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.module.shop.domain.vo.CartVO;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductVO;
import cn.lx.worldcoffee.module.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "咖啡商城模块", description = "商品浏览、购物车、下单、订单查询")
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    // ==================== 商品 ====================

    @Operation(summary = "商品列表", description = "分页获取上架中的咖啡商品，按创建时间倒序")
    @GetMapping("/products")
    public Result<List<ProductVO>> listProducts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return Result.success(shopService.listProducts(page, size));
    }

    @Operation(summary = "商品详情", description = "获取单个咖啡商品的完整信息")
    @GetMapping("/products/{id}")
    public Result<ProductVO> productDetail(
            @Parameter(description = "商品ID") @PathVariable Long id) {
        return Result.success(shopService.getProductDetail(id));
    }

    // ==================== 购物车 ====================

    @Operation(summary = "加入购物车", description = "将商品加入购物车，已存在则累加数量")
    @PostMapping("/cart")
    public Result<Void> addToCart(@Valid @RequestBody AddCartFrom from) {
        shopService.addToCart(from);
        return Result.success(null);
    }

    @Operation(summary = "我的购物车", description = "查看当前用户购物车中的所有商品")
    @GetMapping("/cart")
    public Result<List<CartVO>> myCart() {
        return Result.success(shopService.listCart());
    }

    @Operation(summary = "修改购物车数量", description = "修改指定购物车项的商品数量")
    @PutMapping("/cart/{id}")
    public Result<Void> updateCartQuantity(
            @Parameter(description = "购物车项ID") @PathVariable Long id,
            @Parameter(description = "新数量") @RequestParam int quantity) {
        shopService.updateCartQuantity(id, quantity);
        return Result.success(null);
    }

    @Operation(summary = "删除购物车项", description = "从购物车移除指定商品")
    @DeleteMapping("/cart/{id}")
    public Result<Void> removeFromCart(
            @Parameter(description = "购物车项ID") @PathVariable Long id) {
        shopService.removeFromCart(id);
        return Result.success(null);
    }

    // ==================== 订单 ====================

    @Operation(summary = "提交订单", description = "将购物车中的商品生成订单，清空购物车，扣减库存")
    @PostMapping("/orders")
    public Result<OrderVO> createOrder(@Valid @RequestBody CreateOrderFrom from) {
        return Result.success(shopService.createOrder(from));
    }

    @Operation(summary = "我的订单", description = "分页查看当前用户的所有订单，按时间倒序")
    @GetMapping("/orders")
    public Result<List<OrderVO>> myOrders(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return Result.success(shopService.listOrders(page, size));
    }

    @Operation(summary = "订单详情", description = "查看单个订单的完整信息，包括订单明细")
    @GetMapping("/orders/{id}")
    public Result<OrderVO> orderDetail(
            @Parameter(description = "订单ID") @PathVariable Long id) {
        return Result.success(shopService.getOrderDetail(id));
    }
}
