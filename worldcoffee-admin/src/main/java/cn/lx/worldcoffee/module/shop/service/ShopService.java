package cn.lx.worldcoffee.module.shop.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.module.shop.dao.CartItemDao;
import cn.lx.worldcoffee.module.shop.dao.CoffeeOrderDao;
import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.dao.OrderItemDao;
import cn.lx.worldcoffee.module.shop.domain.CartItem;
import cn.lx.worldcoffee.module.shop.domain.CoffeeOrder;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import cn.lx.worldcoffee.module.shop.domain.OrderItem;
import cn.lx.worldcoffee.module.shop.domain.from.AddCartFrom;
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.module.shop.domain.vo.CartVO;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ShopService {
    private final CoffeeProductDao productDao;
    private final CartItemDao cartItemDao;
    private final CoffeeOrderDao orderDao;
    private final OrderItemDao orderItemDao;

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
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(CreateOrderFrom from) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        // 1. 查购物车
        List<CartItem> cartItems = cartItemDao.selectList(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
        if (cartItems.isEmpty()) throw new RuntimeException("购物车是空的");

        // 2. 批量查商品，校验库存
        List<Long> productIds = cartItems.stream().map(CartItem::getProductId).collect(Collectors.toList());
        Map<Long, CoffeeProduct> productMap = productDao.selectBatchIds(productIds)
                .stream().collect(Collectors.toMap(CoffeeProduct::getId, p -> p));

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cart : cartItems) {
            CoffeeProduct p = productMap.get(cart.getProductId());
            if (p == null || p.getStatus() == 0) throw new RuntimeException("商品已下架：" + cart.getProductId());
            if (cart.getQuantity() > p.getStock()) throw new RuntimeException("库存不足：" + p.getName());

            // 订单明细（存快照，防止商品改名后历史订单对不上）
            OrderItem oi = new OrderItem();
            oi.setProductId(p.getId());
            oi.setProductName(p.getName());
            oi.setPrice(p.getPrice());
            oi.setQuantity(cart.getQuantity());
            orderItems.add(oi);

            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
        }

        // 3. 生成订单编号：时间戳 + 用户ID后4位 + 随机3位
        String orderNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", userId % 10000)
                + String.format("%03d", new Random().nextInt(1000));

        // 4. 插入订单
        CoffeeOrder order = new CoffeeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setStatus(0);  // 待支付
        order.setAddress(from.getAddress());
        order.setRemark(from.getRemark());
        order.setCreateTime(LocalDateTime.now());
        // SQL: INSERT INTO coffee_order (order_no, user_id, total_amount, status, ...) VALUES (?, ?, ?, ?, ...)
        orderDao.insert(order);

        // 5. 批量插入订单明细
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            // SQL: INSERT INTO order_item (order_id, product_id, product_name, price, quantity) VALUES (?, ?, ?, ?, ?)
            orderItemDao.insert(item);
        }

        // 6. 批量扣库存
        for (CartItem cart : cartItems) {
            // SQL: UPDATE coffee_product SET stock = stock - ? WHERE id = ? AND stock >= ?
            int affected = productDao.update(null, new LambdaUpdateWrapper<CoffeeProduct>()
                    .setSql("stock = stock - " + cart.getQuantity())
                    .eq(CoffeeProduct::getId, cart.getProductId())
                    .ge(CoffeeProduct::getStock, cart.getQuantity()));
            if (affected == 0) throw new RuntimeException("库存扣减失败，请重新下单");
        }

        // 7. 清空购物车
        // SQL: DELETE FROM cart_item WHERE user_id = ?
        cartItemDao.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));

        // 8. 组装VO返回
        List<OrderVO.OrderItemVO> itemVOs = orderItems.stream().map(i -> OrderVO.OrderItemVO.builder()
                .productId(i.getProductId())
                .productName(i.getProductName())
                .price(i.getPrice())
                .quantity(i.getQuantity())
                .build()).collect(Collectors.toList());

        return OrderVO.builder()
                .id(order.getId())
                .orderNo(orderNo)
                .totalAmount(total)
                .status(0)
                .address(from.getAddress())
                .remark(from.getRemark())
                .createTime(order.getCreateTime())
                .items(itemVOs)
                .build();
    }

    public List<OrderVO> listOrders(int page, int size) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        // SQL: SELECT * FROM coffee_order WHERE user_id = ? ORDER BY create_time DESC LIMIT ?,?
        List<CoffeeOrder> orders = orderDao.selectList(new LambdaQueryWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getUserId, userId)
                .orderByDesc(CoffeeOrder::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size));

        return orders.stream().map(o -> OrderVO.builder()
                .id(o.getId())
                .orderNo(o.getOrderNo())
                .totalAmount(o.getTotalAmount())
                .status(o.getStatus())
                .address(o.getAddress())
                .remark(o.getRemark())
                .createTime(o.getCreateTime())
                .build()).collect(Collectors.toList());
    }

    public OrderVO getOrderDetail(Long orderId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        // 1. 查订单
        // SQL: SELECT * FROM coffee_order WHERE id = ?
        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new RuntimeException("无权查看该订单");

        // 2. 查订单明细
        // SQL: SELECT * FROM order_item WHERE order_id = ?
        List<OrderItem> items = orderItemDao.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        // 3. 组装返回
        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .address(order.getAddress())
                .remark(order.getRemark())
                .createTime(order.getCreateTime())
                .items(items.stream().map(i -> OrderVO.OrderItemVO.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .price(i.getPrice())
                        .quantity(i.getQuantity())
                        .build()).collect(Collectors.toList()))
                .build();
    }
}
