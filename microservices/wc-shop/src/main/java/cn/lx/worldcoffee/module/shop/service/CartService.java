package cn.lx.worldcoffee.module.shop.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.dao.CartItemDao;
import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.domain.CartItem;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import cn.lx.worldcoffee.module.shop.domain.from.AddCartFrom;
import cn.lx.worldcoffee.module.shop.domain.vo.CartVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车服务。
 *
 * 职责：
 *   - 加入购物车（已有则累加数量，没有则新增）
 *   - 查看购物车列表（批量查商品，修 N+1 问题）
 *   - 修改购物车数量（校验库存上限）
 *   - 删除购物车项
 *
 * 为什么单独拆出来：
 *   购物车是用户"下单前"的操作，跟订单（下单后）是不同阶段。
 *   购物车不涉及事务、不涉及库存扣减，逻辑相对简单。
 *
 * 依赖：
 *   - CartItemDao：购物车表 CRUD
 *   - CoffeeProductDao：查商品信息（名称、价格、库存、图片）
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemDao cartItemDao;
    private final CoffeeProductDao productDao;

    /**
     * 加入购物车。
     *
     * 逻辑：
     *   1. 校验商品存在且上架
     *   2. 查购物车是否已有该商品
     *      - 有 → 累加数量（校验库存上限）
     *      - 无 → 新增一条购物车记录
     *
     * 为什么加购时就校验库存：
     *   如果不校验，用户能往购物车里加 100 件，到下单时才发现没货，体验很差。
     *   加购时提前拦住，告诉用户"库存不够了"。
     */
    public void addToCart(AddCartFrom from) {
        Long userId = SecurityUtils.requireUserId();

        // 1. 校验商品存在且上架
        CoffeeProduct product = productDao.selectById(from.getProductId());
        if (product == null || product.getStatus() == 0) {
            throw new ServiceException("商品不存在或者已经下架");
        }

        // 2. 查购物车是否已有该商品
        // SQL: SELECT * FROM cart_item WHERE user_id = ? AND product_id = ?
        CartItem existing = cartItemDao.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, product.getId()));

        if (existing != null) {
            // 已有 → 累加数量
            int newQty = existing.getQuantity() + from.getQuantity();
            if (newQty > product.getStock()) {
                throw new ServiceException("库存不足，当前库存：" + product.getStock());
            }
            existing.setQuantity(newQty);
            // SQL: UPDATE cart_item SET quantity = ? WHERE id = ?
            cartItemDao.updateById(existing);
        } else {
            // 没有 → 新增
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(from.getProductId());
            item.setQuantity(from.getQuantity());
            item.setCreateTime(LocalDateTime.now());
            // SQL: INSERT INTO cart_item (user_id, product_id, quantity) VALUES (?, ?, ?)
            cartItemDao.insert(item);
        }
    }

    /**
     * 查看购物车列表。
     *
     * 逻辑：
     *   1. 查当前用户的购物车记录
     *   2. 批量查商品信息（一次 IN 查询，避免 N+1）
     *   3. 组装 CartVO 返回
     *
     * 什么是 N+1 问题：
     *   如果购物车有 5 件商品，先查 cart_item 得到 5 条记录，
     *   然后 for 循环里每条都 selectById 查一次商品 → 1 + 5 = 6 次 SQL。
     *   批量查：selectBatchIds([id1,id2,id3,id4,id5]) → 1 + 1 = 2 次 SQL。
     */
    public List<CartVO> listCart() {
        Long userId = SecurityUtils.requireUserId();

        // 1. 查购物车
        // SQL: SELECT * FROM cart_item WHERE user_id = ? ORDER BY create_time DESC
        List<CartItem> items = cartItemDao.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getCreateTime));
        if (items.isEmpty()) return List.of();

        // 2. 批量查商品（修 N+1）
        List<Long> productIds = items.stream()
                .map(CartItem::getProductId).collect(Collectors.toList());
        // SQL: SELECT * FROM coffee_product WHERE id IN (?,?,?)
        Map<Long, CoffeeProduct> productMap = productDao.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(CoffeeProduct::getId, p -> p));

        // 3. 组装 VO
        return items.stream().map(item -> {
            CoffeeProduct p = productMap.get(item.getProductId());
            return CartVO.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .productName(p != null ? p.getName() : "已下架")
                    .price(p != null ? p.getPrice() : BigDecimal.ZERO)
                    .image(parseFirstImage(p != null ? p.getImages() : null))
                    .quantity(item.getQuantity())
                    .stock(p != null ? p.getStock() : 0)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 修改购物车数量。
     *
     * 校验点：
     *   1. 购物车项存在 且 属于当前用户（防止用户 A 改用户 B 的购物车）
     *   2. 数量 >= 1
     *   3. 数量 <= 商品库存
     */
    public void updateCartQuantity(Long cartItemId, int quantity) {
        Long userId = SecurityUtils.requireUserId();
        if (quantity < 1) throw new ServiceException("数量至少为1");

        // SQL: SELECT * FROM cart_item WHERE id = ?
        CartItem item = cartItemDao.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new ServiceException("购物车项不存在");
        }

        CoffeeProduct product = productDao.selectById(item.getProductId());
        if (product == null || product.getStatus() == 0) {
            throw new ServiceException("商品不存在或已下架");
        }
        if (quantity > product.getStock()) {
            throw new ServiceException("库存不足，当前库存：" + product.getStock());
        }

        // SQL: UPDATE cart_item SET quantity = ? WHERE id = ?
        item.setQuantity(quantity);
        cartItemDao.updateById(item);
    }

    /**
     * 删除购物车项。
     * 同样校验所属用户，防止越权操作。
     */
    public void removeFromCart(Long cartItemId) {
        Long userId = SecurityUtils.requireUserId();

        CartItem item = cartItemDao.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new ServiceException("购物车项不存在");
        }

        // SQL: DELETE FROM cart_item WHERE id = ?
        cartItemDao.deleteById(cartItemId);
    }

    // ==================== 内部工具 ====================

    /**
     * 从 JSON 图片数组中取第一张。
     *
     * 为什么需要这个方法：
     *   coffee_product.images 存的是 ["a.jpg","b.jpg"] 这种 JSON 数组字符串，
     *   购物车列表只展示一张首图，不需要全量，所以解析 JSON 取第一个元素。
     */
    private String parseFirstImage(String images) {
        if (images == null || images.isBlank()) return null;
        List<String> list = JSONUtil.toList(images, String.class);
        return list.isEmpty() ? null : list.get(0);
    }
}
