package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.domain.EsProduct;
import cn.lx.worldcoffee.module.shop.domain.from.AddCartFrom;
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.module.shop.domain.from.ProductForm;
import cn.lx.worldcoffee.module.shop.domain.vo.*;
import cn.lx.worldcoffee.module.shop.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商城 Controller —— 只做"接收请求 → 调 Service → 返回结果"。
 *
 * 重构后不再注入一个大而全的 ShopService，
 * 而是按职责注入 6 个细分 Service：
 *   - ProductService：商品 + 分类
 *   - CartService：购物车
 *   - OrderService：订单
 *   - AddressService：收货地址
 *   - InventoryService：库存管理
 *   - LogisticsService：物流
 * 另外还保留 EsSearchService（搜索）和 PaymentService（支付）。
 */
@Tag(name = "咖啡商城模块", description = "商品浏览、购物车、下单、订单查询")
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;
    private final AddressService addressService;
    private final InventoryService inventoryService;
    private final LogisticsService logisticsService;
    private final EsSearchService esSearchService;
    private final PaymentService paymentService;

    // ==================== 商品 ====================

    @Operation(summary = "商品列表", description = "分页获取上架中的咖啡商品，按创建时间倒序，可按分类筛选")
    @GetMapping("/products")
    public Result<List<ProductVO>> listProducts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId) {
        return Result.success(productService.listProducts(page, size, categoryId));
    }

    @Operation(summary = "商品详情", description = "获取单个咖啡商品的完整信息")
    @GetMapping("/products/{id}")
    public Result<ProductVO> productDetail(
            @Parameter(description = "商品ID") @PathVariable Long id) {
        return Result.success(productService.getProductDetail(id));
    }

    @Operation(summary = "搜索商品", description = "通过 ElasticSearch 搜索商品名称和描述")
    @GetMapping("/products/search")
    public Result<List<ProductVO>> searchProducts(
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword) {
        List<EsProduct> esProducts = esSearchService.search(keyword);
        // EsProduct 转 ProductVO 返回给前端
        return Result.success(esProducts.stream().map(e -> ProductVO.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .price(e.getPrice())
                .origin(e.getOrigin())
                .roastLevel(e.getRoastLevel())
                .stock(null)  // ES 没存库存，前端不展示也没事
                .sales(null)
                .build()).collect(Collectors.toList()));
    }

    @Operation(summary = "新增商品", description = "创建新商品并同步到 ES")
    @PostMapping("/products")
    public Result<ProductVO> createProduct(@Valid @RequestBody ProductForm form) {
        return Result.success(productService.createProduct(form));
    }

    @Operation(summary = "修改商品", description = "修改商品信息并同步到 ES")
    @PutMapping("/products/{id}")
    public Result<ProductVO> updateProduct(
            @Parameter(description = "商品ID") @PathVariable Long id,
            @Valid @RequestBody ProductForm form) {
        return Result.success(productService.updateProduct(id, form));
    }

    @Operation(summary = "删除商品", description = "删除商品并从 ES 移除")
    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(
            @Parameter(description = "商品ID") @PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success(null);
    }

    // ==================== 分类 ====================

    @Operation(summary = "分类列表", description = "获取所有商品分类")
    @GetMapping("/categories")
    public Result<List<CategoryVO>> listCategories() {
        return Result.success(productService.listCategories());
    }

    @Operation(summary = "新增分类", description = "创建新的商品分类")
    @PostMapping("/categories")
    public Result<CategoryVO> createCategory(@RequestParam String name) {
        return Result.success(productService.createCategory(name));
    }

    @Operation(summary = "删除分类", description = "删除商品分类")
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        productService.deleteCategory(id);
        return Result.success(null);
    }

    // ==================== 购物车 ====================

    @Operation(summary = "加入购物车", description = "将商品加入购物车，已存在则累加数量")
    @PostMapping("/cart")
    public Result<Void> addToCart(@Valid @RequestBody AddCartFrom from) {
        cartService.addToCart(from);
        return Result.success(null);
    }

    @Operation(summary = "我的购物车", description = "查看当前用户购物车中的所有商品")
    @GetMapping("/cart")
    public Result<List<CartVO>> myCart() {
        return Result.success(cartService.listCart());
    }

    @Operation(summary = "修改购物车数量", description = "修改指定购物车项的商品数量")
    @PutMapping("/cart/{id}")
    public Result<Void> updateCartQuantity(
            @Parameter(description = "购物车项ID") @PathVariable Long id,
            @Parameter(description = "新数量") @RequestParam int quantity) {
        cartService.updateCartQuantity(id, quantity);
        return Result.success(null);
    }

    @Operation(summary = "删除购物车项", description = "从购物车移除指定商品")
    @DeleteMapping("/cart/{id}")
    public Result<Void> removeFromCart(
            @Parameter(description = "购物车项ID") @PathVariable Long id) {
        cartService.removeFromCart(id);
        return Result.success(null);
    }

    // ==================== 订单 ====================

    @Operation(summary = "提交订单", description = "将购物车中的商品生成订单，清空购物车，扣减库存")
    @PostMapping("/orders")
    public Result<OrderVO> createOrder(@Valid @RequestBody CreateOrderFrom from) {
        return Result.success(orderService.createOrder(from));
    }

    @Operation(summary = "我的订单", description = "分页查看当前用户的所有订单，按时间倒序")
    @GetMapping("/orders")
    public Result<List<OrderVO>> myOrders(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "订单状态 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消") @RequestParam(required = false) Integer status) {
        return Result.success(orderService.listOrders(page, size, status));
    }

    @Operation(summary = "订单详情", description = "查看单个订单的完整信息，包括订单明细")
    @GetMapping("/orders/{id}")
    public Result<OrderVO> orderDetail(
            @Parameter(description = "订单ID") @PathVariable Long id) {
        return Result.success(orderService.getOrderDetail(id));
    }

    @Operation(summary = "按订单号查询", description = "根据订单编号查询订单（支付回调、轮询支付状态用）")
    @GetMapping("/order/no/{orderNo}")
    public Result<OrderVO> getByOrderNo(
            @Parameter(description = "订单编号") @PathVariable String orderNo) {
        OrderVO order = orderService.getOrderByOrderNo(orderNo);
        if (order == null) {
            return Result.success(null);
        }
        return Result.success(order);
    }

    @Operation(summary = "取消订单", description = "取消待支付的订单，恢复库存")
    @PatchMapping("/orders/{id}/cancel")
    public Result<Void> cancelOrder(
            @Parameter(description = "订单ID") @PathVariable Long id) {
        orderService.cancelOrder(id);
        return Result.success(null);
    }

    @Operation(summary = "更新订单状态", description = "订单状态流转：待支付→已支付→已发货→已完成")
    @PatchMapping("/orders/{id}/status")
    public Result<Void> updateOrderStatus(
            @Parameter(description = "订单ID") @PathVariable Long id,
            @Parameter(description = "目标状态") @RequestParam Integer status) {
        orderService.updateOrderStatus(id, status);
        return Result.success(null);
    }

    @Operation(summary = "发起支付", description = "创建支付单，返回 Mock 支付信息")
    @PostMapping("/orders/{id}/pay")
    public Result<PaymentResultVO> payOrder(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();

        // 根据订单 ID 拿到订单号
        OrderVO order = orderService.getOrderDetail(id);
        if (order == null) throw new ServiceException("订单不存在");

        return Result.success(paymentService.createPayment(userId, order.getOrderNo()));
    }

    // ==================== 收货地址 ====================

    @Operation(summary = "地址列表", description = "查看当前用户的所有收货地址")
    @GetMapping("/addresses")
    public Result<List<AddressVO>> listAddresses() {
        return Result.success(addressService.listAddresses());
    }

    @Operation(summary = "地址详情", description = "查看单个收货地址")
    @GetMapping("/addresses/{id}")
    public Result<AddressVO> getAddress(
            @Parameter(description = "地址ID") @PathVariable Long id) {
        return Result.success(addressService.getAddress(id));
    }

    @Operation(summary = "新增地址", description = "添加新收货地址")
    @PostMapping("/addresses")
    public Result<AddressVO> createAddress(@Valid @RequestBody AddressForm form) {
        return Result.success(addressService.createAddress(form));
    }

    @Operation(summary = "修改地址", description = "修改收货地址信息")
    @PutMapping("/addresses/{id}")
    public Result<AddressVO> updateAddress(
            @Parameter(description = "地址ID") @PathVariable Long id,
            @Valid @RequestBody AddressForm form) {
        return Result.success(addressService.updateAddress(id, form));
    }

    @Operation(summary = "删除地址", description = "删除收货地址")
    @DeleteMapping("/addresses/{id}")
    public Result<Void> deleteAddress(
            @Parameter(description = "地址ID") @PathVariable Long id) {
        addressService.deleteAddress(id);
        return Result.success(null);
    }

    // ==================== 管理员后台 ====================

    @Operation(summary = "修改库存", description = "管理员手动设置商品库存（MySQL + Redis 双写）")
    @PutMapping("/products/{id}/stock")
    public Result<Void> updateStock(@PathVariable Long id, @RequestParam Integer stock) {
        inventoryService.updateStock(id, stock);
        return Result.success(null);
    }

    // ==================== 物流 ====================

    @Operation(summary = "发货（管理员）", description = "填写快递公司和单号，生成 Mock 物流轨迹")
    @PostMapping("/orders/{id}/ship")
    public Result<Void> shipOrder(
            @PathVariable Long id,
            @RequestParam String shippingCompany,
            @RequestParam String trackingNo) {
        logisticsService.shipOrder(id, shippingCompany, trackingNo);
        return Result.success(null);
    }

    @Operation(summary = "查询物流", description = "查看订单的物流轨迹")
    @GetMapping("/orders/{id}/logistics")
    public Result<LogisticsVO> getLogistics(@PathVariable Long id) {
        return Result.success(logisticsService.getLogistics(id));
    }

    @Operation(summary = "确认收货", description = "用户确认收货，订单状态变为已完成")
    @PatchMapping("/orders/{id}/confirm")
    public Result<Void> confirmReceipt(@PathVariable Long id) {
        logisticsService.confirmReceipt(id);
        return Result.success(null);
    }
}
