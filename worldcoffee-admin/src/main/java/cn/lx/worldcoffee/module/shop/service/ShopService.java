package cn.lx.worldcoffee.module.shop.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.module.shop.dao.CartItemDao;
import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.domain.CartItem;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import cn.lx.worldcoffee.module.shop.domain.from.AddCartFrom;
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.module.shop.domain.vo.CartVO;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ShopService {
    private final CoffeeProductDao productDao;
    private final CartItemDao cartItemDao;

    private Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return Long.valueOf(auth.getPrincipal().toString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    public List<ProductVO> listProducts(int page, int size) {
        //1. LambdaQueryWrapper 拼查询条件
        //   └─ WHERE status = 1 ORDER BY create_time DESC LIMIT ?,?
        //
        //2. List<CoffeeProduct> → List<ProductVO>
        //   └─ Entity 转 VO，只保留前端需要的字段

        // SQL: SELECT * FROM coffee_product WHERE status = 1
        //      ORDER BY create_time DESC LIMIT ?,?
        List<CoffeeProduct> products = productDao.selectList(new LambdaQueryWrapper<CoffeeProduct>()
                .eq(CoffeeProduct::getStatus, 1)
                .orderByDesc(CoffeeProduct::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size)
        );

        return products.stream().map(p -> ProductVO.builder()
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
                .build()).collect(Collectors.toList());
    }

    public ProductVO getProductDetail(Long id) {
        // SQL: SELECT * FROM coffee_product WHERE id = ?
        CoffeeProduct product = productDao.selectById(id);
        if (product == null || product.getStatus() == 0) {
            throw new RuntimeException("商品不存在");
        }
        // entity → VO，字段一一对应
        return ProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .images(product.getImages())
                .origin(product.getOrigin())
                .roastLevel(product.getRoastLevel())
                .weight(product.getWeight())
                .stock(product.getStock())
                .sales(product.getSales())
                .build();
    }

//    思路：查购物车是否已有该商品 → 有则改数量，无则新增。
    public void addToCart(AddCartFrom from) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        // 1. 校验商品存在且上架
        CoffeeProduct product = productDao.selectById(from.getProductId());
        if (product == null || product.getStatus() == 0){
            throw new RuntimeException("商品不存在或者已经下架");
        }

        // 2. 查购物车是否有该商品
        // SQL: SELECT * FROM cart_item WHERE user_id = ? AND product_id = ?
        CartItem existing = cartItemDao.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, product.getId())
        );

        if (existing != null){
            // 已有 → 加数量（比如再点一次加购，数量+1）
            int newQty = existing.getQuantity() + from.getQuantity();
            if (newQty > product.getStock()) {
                //举个例子：假设一款豆子仓库里只剩 5袋（stock = 5）。
                // 你购物车里已经有 3袋了，这时候你又点了"加购3袋"（from.getQuantity() = 3）。
                // 那 newQty = 3 + 3 = 6，你要买6袋，但仓库只有5袋，6 > 5，
                // 所以抛异常"库存不足"。如果不做这个检查，用户就能往购物车里加100袋，
                // 到下单的时候才发现没货，体验就很差了。所以加购的时候就提前拦住，
                // 告诉用户"你加不了了，库存不够了"。
                throw new RuntimeException("库存不足，当前库存：" + product.getStock());
            }
            existing.setQuantity(newQty);
            // SQL: UPDATE cart_item SET quantity = ? WHERE id = ?
            cartItemDao.updateById(existing);
        }else {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(from.getProductId());
            item.setQuantity(from.getQuantity());
            item.setCreateTime(LocalDateTime.now());
            // SQL: INSERT INTO cart_item (user_id, product_id, quantity) VALUES (?, ?, ?)
            cartItemDao.insert(item);
        }
    }

//    思路：查当前用户的购物车列表 → 批量查商品信息 → 组装 VO。
    public List<CartVO> listCart() {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        // 1. 查购物车
        // SQL: SELECT * FROM cart_item WHERE user_id = ? ORDER BY create_time DESC
        List<CartItem> items = cartItemDao.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getCreateTime)
        );
        if (items.isEmpty()) return List.of();

        // 2. 批量查商品（修 N+1）
        List<Long> productIds = items.stream()
                .map(CartItem::getProductId).collect(Collectors.toList());
        // SQL: SELECT * FROM coffee_product WHERE id IN (?,?,?)
        Map<Long, CoffeeProduct> productMap = productDao.selectBatchIds(productIds).stream().
                collect(Collectors.toMap(CoffeeProduct::getId, p -> p));

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
     * 为什么有个 parseFirstImage：coffee_product.images
     * 存的是 ["a.jpg","b.jpg"] JSON 数组字符串，购物车列表只展示一张首图，不需要全量。
     *
     */
    // 从 JSON 图片数组中取第一张
    private String parseFirstImage(String images) {
        if (images == null || images.isBlank()) return null;
        // 和 coffee_post 同样的 JSON 数组格式，复用 Hutool
        List<String> list = JSONUtil.toList(images, String.class);
        return list.isEmpty() ? null : list.get(0);
    }

    public void updateCartQuantity(Long cartItemId, int quantity) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        if (quantity < 1) throw new RuntimeException("数量至少为1");

        // 1. 查购物车项，校验所属用户
        // SQL: SELECT * FROM cart_item WHERE id = ?
        CartItem item = cartItemDao.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new RuntimeException("购物车项不存在");
        }
        // 2. 校验库存
        CoffeeProduct product = productDao.selectById(item.getProductId());
        if (product == null || product.getStatus() == 0) {
            throw new RuntimeException("商品不存在或已下架");
        }
        if (quantity > product.getStock()) {
            throw new RuntimeException("库存不足，当前库存：" + product.getStock());
        }
        // 3. 更新数量
        // SQL: UPDATE cart_item SET quantity = ? WHERE id = ?
        item.setQuantity(quantity);
        cartItemDao.updateById(item);
        /**
         * 为什么校验 item.getUserId().equals(userId)：
         * 用户 A 不能通过传购物车 ID 改用户 B 的购物车。和帖子删除同理。
         */
    }

    public void removeFromCart(Long cartItemId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        // 校验所属用户
        CartItem item = cartItemDao.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new RuntimeException("购物车项不存在");
        }

        // SQL: DELETE FROM cart_item WHERE id = ?
        cartItemDao.deleteById(cartItemId);
    }

//    下单是商城最核心的一个方法，涉及事务和库存扣减。思路

    /**
     * 1. 查购物车（只查当前用户的）
     * 2. 校验商品都存在、库存够
     * 3. 生成订单编号
     * 4. 计算总金额
     * 5. 插入订单表（coffee_order）
     * 6. 批量插入订单明细（order_item，快照商品名+价格）
     * 7. 批量扣减库存（UPDATE stock = stock - ?）
     * 8. 清空购物车
     *       全部在一个事务里，任何一步失败整体回滚
     *
     */
    public OrderVO createOrder(CreateOrderFrom from) {
        return null;
    }

    public List<OrderVO> listOrders(int page, int size) {
        return null;
    }

    public OrderVO getOrderDetail(Long id) {
        return null;
    }


    /**
     * 红色 = 未被 git 跟踪（新创建的文件，还没 git add）
     *
     * 蓝色 = 已被 git 跟踪且有改动
     *
     * 说白了就是：我帮你生成的 shop/ 下那几个文件还没被 git 纳入管理，
     * 你 git add 一下就会变绿/蓝了。不影响编译和运行，只是 IDE 的 git 状态标识。
     */
}
