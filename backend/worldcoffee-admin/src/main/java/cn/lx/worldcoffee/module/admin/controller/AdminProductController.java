package cn.lx.worldcoffee.module.admin.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.admin.service.AdminService;
import cn.lx.worldcoffee.module.shop.domain.from.ProductForm;
import cn.lx.worldcoffee.module.shop.domain.vo.CategoryVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductVO;
import cn.lx.worldcoffee.module.shop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品管理控制器
 *
 * GET    /api/admin/products             — 全量商品列表（含下架商品，可按分类/状态筛选）
 * POST   /api/admin/products             — 新增商品
 * PUT    /api/admin/products/{id}        — 编辑商品
 * DELETE /api/admin/products/{id}        — 删除商品
 * POST   /api/admin/products/{id}/toggle — 切换上下架状态
 *
 * GET    /api/admin/categories           — 分类列表
 * POST   /api/admin/categories           — 新增分类
 * DELETE /api/admin/categories/{id}      — 删除分类
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminService adminService;
    private final ProductService productService;

    // ==================== 商品 ====================

    /** 全量商品列表（管理员视角，包含下架商品） */
    @GetMapping("/products")
    public Result<List<ProductVO>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        return Result.success(adminService.listAllProducts(page, size, categoryId, status));
    }

    /** 新增商品（复用前台 ProductService） */
    @PostMapping("/products")
    public Result<ProductVO> createProduct(@RequestBody ProductForm form) {
        return Result.success(productService.createProduct(form));
    }

    /** 编辑商品 */
    @PutMapping("/products/{id}")
    public Result<ProductVO> updateProduct(@PathVariable Long id, @RequestBody ProductForm form) {
        return Result.success(productService.updateProduct(id, form));
    }

    /** 删除商品（同时从 ES 索引中移除） */
    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success(null);
    }

    /** 切换上下架状态（1→0 或 0→1） */
    @PostMapping("/products/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        adminService.toggleProductStatus(id);
        return Result.success(null);
    }

    // ==================== 分类 ====================

    /** 分类列表 */
    @GetMapping("/categories")
    public Result<List<CategoryVO>> listCategories() {
        return Result.success(adminService.listCategories());
    }

    /** 新增分类 */
    @PostMapping("/categories")
    public Result<CategoryVO> createCategory(@RequestParam String name) {
        return Result.success(adminService.createCategory(name));
    }

    /** 删除分类 */
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return Result.success(null);
    }
}
