package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.shop.domain.Coupon;
import cn.lx.worldcoffee.module.shop.domain.from.ProductForm;
import cn.lx.worldcoffee.module.shop.domain.vo.CategoryVO;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductVO;
import cn.lx.worldcoffee.module.shop.service.AdminShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminShopController {

    private final AdminShopService adminShopService;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(adminShopService.getDashboardStats());
    }

    @GetMapping("/products")
    public Result<List<ProductVO>> listProducts(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) Integer status) {
        return Result.success(adminShopService.listAllProducts(page, size, categoryId, status));
    }

    @PostMapping("/products")
    public Result<ProductVO> createProduct(@RequestBody ProductForm form) {
        return Result.success(adminShopService.createProduct(form));
    }

    @PutMapping("/products/{id}")
    public Result<ProductVO> updateProduct(@PathVariable Long id, @RequestBody ProductForm form) {
        return Result.success(adminShopService.updateProduct(id, form));
    }

    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        adminShopService.deleteProduct(id);
        return Result.success(null);
    }

    @PostMapping("/products/{id}/toggle")
    public Result<Void> toggleProductStatus(@PathVariable Long id) {
        adminShopService.toggleProductStatus(id);
        return Result.success(null);
    }

    @GetMapping("/categories")
    public Result<List<CategoryVO>> listCategories() {
        return Result.success(adminShopService.listCategories());
    }

    @PostMapping("/categories")
    public Result<CategoryVO> createCategory(@RequestParam String name) {
        return Result.success(adminShopService.createCategory(name));
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        adminShopService.deleteCategory(id);
        return Result.success(null);
    }

    @GetMapping("/orders")
    public Result<List<OrderVO>> listOrders(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String orderNo) {
        return Result.success(adminShopService.listAllOrders(page, size, status, userId, orderNo));
    }

    @GetMapping("/orders/{id}")
    public Result<OrderVO> getOrder(@PathVariable Long id) {
        return Result.success(adminShopService.getOrderDetail(id));
    }

    @PostMapping("/orders/{id}/ship")
    public Result<Void> shipOrder(@PathVariable Long id,
                                  @RequestParam String shippingCompany,
                                  @RequestParam String trackingNo) {
        adminShopService.shipOrder(id, shippingCompany, trackingNo);
        return Result.success(null);
    }

    @GetMapping("/marketing/coupons")
    public Result<List<Map<String, Object>>> listCoupons(@RequestParam(required = false) Integer type) {
        return Result.success(adminShopService.listCoupons(type));
    }

    @PostMapping("/marketing/coupons")
    public Result<Coupon> createCoupon(@RequestBody Coupon coupon) {
        return Result.success(adminShopService.createCoupon(coupon));
    }

    @PutMapping("/marketing/coupons/{id}")
    public Result<Void> updateCoupon(@PathVariable Long id, @RequestBody Coupon coupon) {
        adminShopService.updateCoupon(id, coupon);
        return Result.success(null);
    }

    @DeleteMapping("/marketing/coupons/{id}")
    public Result<Void> deleteCoupon(@PathVariable Long id) {
        adminShopService.deleteCoupon(id);
        return Result.success(null);
    }

    @PostMapping("/marketing/coupons/{id}/toggle")
    public Result<Void> toggleCouponStatus(@PathVariable Long id) {
        adminShopService.toggleCouponStatus(id);
        return Result.success(null);
    }

    @GetMapping("/marketing/coupons/{id}/products")
    public Result<List<Long>> getCouponProducts(@PathVariable Long id) {
        return Result.success(adminShopService.getCouponProductIds(id));
    }

    @PutMapping("/marketing/coupons/{id}/products")
    public Result<Void> setCouponProducts(@PathVariable Long id, @RequestBody List<Long> productIds) {
        adminShopService.setCouponProducts(id, productIds);
        return Result.success(null);
    }

    @GetMapping("/marketing/coupons/{id}/participants")
    public Result<List<Map<String, Object>>> getParticipants(@PathVariable Long id) {
        return Result.success(adminShopService.getCouponParticipants(id));
    }
}
