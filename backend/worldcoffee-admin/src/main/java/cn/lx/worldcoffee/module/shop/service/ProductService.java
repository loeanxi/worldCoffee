package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.shop.dao.CategoryDao;
import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.domain.Category;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import cn.lx.worldcoffee.module.shop.domain.from.ProductForm;
import cn.lx.worldcoffee.module.shop.domain.vo.CategoryVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品 + 分类管理服务。
 *
 * 职责：
 *   - 商品 CRUD（增删改查 + 分页 + 按分类筛选）
 *   - 分类 CRUD
 *   - 商品变更时同步 ES（ElasticSearch），保证搜索和数据库一致
 *
 * 为什么单独拆出来：
 *   商品是商城的"展示层"核心，但它的逻辑其实很简单——就是查数据库、转 VO、同步 ES。
 *   跟库存（Redis Lua）、订单（事务+扣库存）完全不同领域，混在一起只会让代码更难读。
 *
 * 依赖：
 *   - CoffeeProductDao：商品表 CRUD
 *   - CategoryDao：分类表 CRUD
 *   - EsSearchService：ES 同步（商品变更时通知 ES 更新/删除文档）
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final CoffeeProductDao productDao;
    private final CategoryDao categoryDao;
    private final EsSearchService esSearchService;

    // ==================== 商品查询 ====================

    /**
     * 分页查上架商品（status=1），可按分类过滤。
     * 前端商城首页、分类页都调这个。
     *
     * SQL: SELECT * FROM coffee_product WHERE status = 1 [AND category_id = ?]
     *      ORDER BY create_time DESC LIMIT ?, ?
     */
    public List<ProductVO> listProducts(int page, int size, Long categoryId) {
        LambdaQueryWrapper<CoffeeProduct> wrapper = new LambdaQueryWrapper<CoffeeProduct>()
                .eq(CoffeeProduct::getStatus, 1);
        if (categoryId != null) {
            wrapper.eq(CoffeeProduct::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(CoffeeProduct::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);

        List<CoffeeProduct> products = productDao.selectList(wrapper);
        return products.stream().map(this::toProductVO).collect(Collectors.toList());
    }

    /**
     * 商品详情（单条查询）。
     * 前端商品详情页用。
     */
    public ProductVO getProductDetail(Long id) {
        CoffeeProduct product = productDao.selectById(id);
        if (product == null || product.getStatus() == 0) {
            throw new ServiceException("商品不存在");
        }
        return toProductVO(product);
    }

    // ==================== 商品增删改 ====================

    /**
     * 新增商品。
     * 插入 MySQL 后立即同步到 ES，这样搜索接口马上就能搜到新商品。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductVO createProduct(ProductForm form) {
        CoffeeProduct product = new CoffeeProduct();
        product.setName(form.getName());
        product.setDescription(form.getDescription());
        product.setPrice(form.getPrice());
        product.setImages(form.getImages());
        product.setOrigin(form.getOrigin());
        product.setRoastLevel(form.getRoastLevel());
        product.setWeight(form.getWeight());
        product.setStock(form.getStock() != null ? form.getStock() : 0);
        product.setSales(form.getSales() != null ? form.getSales() : 0);
        product.setStatus(form.getStatus() != null ? form.getStatus() : 1); // 默认上架
        product.setCreateTime(LocalDateTime.now());
        productDao.insert(product);

        // 同步到 ES
        esSearchService.saveProductToEs(product);

        return getProductDetail(product.getId());
    }

    /**
     * 修改商品。
     * ES 里同一 ID 的文档会被新数据覆盖（ES 没有"版本"概念，直接覆盖）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductVO updateProduct(Long id, ProductForm form) {
        CoffeeProduct product = productDao.selectById(id);
        if (product == null) throw new ServiceException("商品不存在");

        product.setName(form.getName());
        product.setDescription(form.getDescription());
        product.setPrice(form.getPrice());
        product.setImages(form.getImages());
        product.setOrigin(form.getOrigin());
        product.setRoastLevel(form.getRoastLevel());
        product.setWeight(form.getWeight());
        product.setStock(form.getStock() != null ? form.getStock() : 0);
        product.setSales(form.getSales() != null ? form.getSales() : 0);
        product.setStatus(form.getStatus() != null ? form.getStatus() : 1);

        productDao.updateById(product);

        // 同步到 ES（覆盖旧文档）
        esSearchService.saveProductToEs(product);

        return getProductDetail(product.getId());
    }

    /**
     * 删除商品（物理删除）。
     * 同时从 ES 移除，否则搜索还能搜出来。
     */
    @Transactional
    public void deleteProduct(Long productId) {
        CoffeeProduct product = productDao.selectById(productId);
        if (product == null) throw new ServiceException("商品不存在");
        productDao.deleteById(productId);

        // 从 ES 移除
        esSearchService.deleteProductFromEs(productId);
    }

    // ==================== 分类 ====================

    /**
     * 分类列表（按 sortOrder 升序，数字小的排前面）。
     */
    public List<CategoryVO> listCategories() {
        List<Category> categories = categoryDao.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder));
        return categories.stream().map(c -> CategoryVO.builder()
                .id(c.getId()).name(c.getName()).build()).collect(Collectors.toList());
    }

    /** 新增分类 */
    public CategoryVO createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setCreateTime(LocalDateTime.now());
        categoryDao.insert(category);
        return CategoryVO.builder()
                .id(category.getId()).name(category.getName()).build();
    }

    /** 删除分类 */
    public void deleteCategory(Long id) {
        categoryDao.deleteById(id);
    }

    // ==================== 内部工具 ====================

    /**
     * Entity → VO 转换。
     * 前端只需要 ProductVO 里定义的字段，不需要把整个 CoffeeProduct 实体暴露出去。
     * 注意：这里加了 status 字段，admin 后台商品列表需要展示上下架状态。
     */
    private ProductVO toProductVO(CoffeeProduct p) {
        return ProductVO.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .images(p.getImages())
                .origin(p.getOrigin())
                .roastLevel(p.getRoastLevel())
                .weight(p.getWeight())
                .stock(p.getStock())
                .sales(p.getSales())
                .status(p.getStatus())
                .build();
    }
}
