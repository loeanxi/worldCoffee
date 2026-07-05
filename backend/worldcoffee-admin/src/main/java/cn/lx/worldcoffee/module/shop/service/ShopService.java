package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.module.shop.dao.*;
import cn.lx.worldcoffee.module.shop.domain.*;
import cn.lx.worldcoffee.module.shop.domain.from.AddCartFrom;
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.module.shop.domain.from.ProductForm;
import cn.lx.worldcoffee.module.shop.domain.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
    private final ShippingAddressDao addressDao;
    private final EsSearchService esSearchService;
    private final CategoryDao categoryDao;
    private final StringRedisTemplate redisTemplate;

    //private DefaultRedisScript<Long> stockLua;  IDE 误报，不是编译错误。但为了让它闭嘴，把初始化放到字段声明里：
    private final DefaultRedisScript<Long> stockLua = new DefaultRedisScript<>() {{
        setLocation(new ClassPathResource("stock.lua"));
        setResultType(Long.class);
    }};
    //private DefaultRedisScript<Long> stockRollbackLua;
    private final DefaultRedisScript<Long> stockRollbackLua = new DefaultRedisScript<>() {{
        setLocation(new ClassPathResource("stock_rollback.lua"));
        setResultType(Long.class);
    }};

//    //初始化脚本
//    @PostConstruct
//    public void initLua() {
//        stockLua = new DefaultRedisScript<>();
//        stockLua.setLocation(new ClassPathResource("stock.lua"));
//        stockLua.setResultType(Long.class);
//
//        stockRollbackLua = new DefaultRedisScript<>();
//        stockRollbackLua.setLocation(new ClassPathResource("stock_rollback.lua"));
//        stockRollbackLua.setResultType(Long.class);
//    }

    //在 Redis 扣减失败时调：rollbackStock(cartItems, cart) → 回滚失败之前的
    //在 catch 里调：rollbackStock(cartItems, null) → 回滚所有
    private void rollbackStock(List<CartItem> cartItems, CartItem failedItem) {
        for (CartItem c : cartItems) {
            if (failedItem != null && c.getProductId().equals(failedItem.getProductId())) break;
            String key = "product:stock:" + c.getProductId();
            redisTemplate.execute(stockRollbackLua, List.of(key), String.valueOf(c.getQuantity()));
        }
    }

    private Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return Long.valueOf(auth.getPrincipal().toString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    public List<ProductVO> listProducts(int page, int size,Long categoryId) {
        //1. LambdaQueryWrapper 拼查询条件
        //   └─ WHERE status = 1 ORDER BY create_time DESC LIMIT ?,?
        //
        //2. List<CoffeeProduct> → List<ProductVO>
        //   └─ Entity 转 VO，只保留前端需要的字段

        // SQL: SELECT * FROM coffee_product WHERE status = 1
        //      ORDER BY create_time DESC LIMIT ?,?



        LambdaQueryWrapper<CoffeeProduct> wrapper = new LambdaQueryWrapper<CoffeeProduct>()
                .eq(CoffeeProduct::getStatus, 1);
        if (categoryId != null) {
            wrapper.eq(CoffeeProduct::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(CoffeeProduct::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);

        List<CoffeeProduct> products = productDao.selectList(wrapper);


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
            throw new ServiceException("商品不存在");
        }
        // entity → VO，字段一一对应
        return ProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
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
        if (userId == null) throw new ServiceException("请先登录");

        // 1. 校验商品存在且上架
        CoffeeProduct product = productDao.selectById(from.getProductId());
        if (product == null || product.getStatus() == 0){
            throw new ServiceException("商品不存在或者已经下架");
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
                throw new ServiceException("库存不足，当前库存：" + product.getStock());
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
        if (userId == null) throw new ServiceException("请先登录");

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
        if (userId == null) throw new ServiceException("请先登录");

        if (quantity < 1) throw new ServiceException("数量至少为1");

        // 1. 查购物车项，校验所属用户
        // SQL: SELECT * FROM cart_item WHERE id = ?
        CartItem item = cartItemDao.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new ServiceException("购物车项不存在");
        }
        // 2. 校验库存
        CoffeeProduct product = productDao.selectById(item.getProductId());
        if (product == null || product.getStatus() == 0) {
            throw new ServiceException("商品不存在或已下架");
        }
        if (quantity > product.getStock()) {
            throw new ServiceException("库存不足，当前库存：" + product.getStock());
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
        if (userId == null) throw new ServiceException("请先登录");

        // 校验所属用户
        CartItem item = cartItemDao.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new ServiceException("购物车项不存在");
        }

        // SQL: DELETE FROM cart_item WHERE id = ?
        cartItemDao.deleteById(cartItemId);
    }

//    下单是商城最核心的一个方法，涉及事务和库存扣减。思路

    /**
     * 1. 查购物车（只查当前用户的）
     * 2. 校验商品都存在库存够
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
        if (userId == null) throw new ServiceException("请先登录");

        // 1. 查购物车
        List<CartItem> cartItems = cartItemDao.selectList(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
        if (cartItems.isEmpty()) throw new ServiceException("购物车是空的");

        // 2. 批量查商品，校验库存
        List<Long> productIds = cartItems.stream().map(CartItem::getProductId).collect(Collectors.toList());
        Map<Long, CoffeeProduct> productMap = productDao.selectBatchIds(productIds)
                .stream().collect(Collectors.toMap(CoffeeProduct::getId, p -> p));

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cart : cartItems) {
            CoffeeProduct p = productMap.get(cart.getProductId());
            if (p == null || p.getStatus() == 0) throw new ServiceException("商品已下架：" + cart.getProductId());
//            if (cart.getQuantity() > p.getStock()) throw new ServiceException("库存不足：" + p.getName());

            String key = "product:stock:" + cart.getProductId();
            String redisStock = redisTemplate.opsForValue().get(key);
            if (redisStock == null || Integer.parseInt(redisStock) < cart.getQuantity()) {
                throw new ServiceException("库存不足");
            }

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



        // 6. 批量扣库存
//        for (CartItem cart : cartItems) {
//            // SQL: UPDATE coffee_product SET stock = stock - ? WHERE id = ? AND stock >= ?
//            int affected = productDao.update(null, new LambdaUpdateWrapper<CoffeeProduct>()
//                    .setSql("stock = stock - " + cart.getQuantity())
//                    .eq(CoffeeProduct::getId, cart.getProductId())
//                    .ge(CoffeeProduct::getStock, cart.getQuantity()));
//            if (affected == 0) throw new ServiceException("库存扣减失败，请重新下单");
//        }
        // 6. 批量扣库存（Redis Lua 原子扣减）
        try {
            for (CartItem cart : cartItems) {
                String key = "product:stock:" + cart.getProductId();
                Long result = redisTemplate.execute(stockLua,List.of(key),String.valueOf(cart.getQuantity()));
                if (result == null || result == 0){
                    // 扣减失败 → 回滚已扣的库存
                    rollbackStock(cartItems,cart);
                    throw new ServiceException("库存不足： " + cart.getProductId());
                }
            }
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


            // 6.5 同步库存到 MySQL（最终一致性）
            for (CartItem cart : cartItems) {
                productDao.update(null, new LambdaUpdateWrapper<CoffeeProduct>()
                        .setSql("stock = stock - " + cart.getQuantity())
                        .eq(CoffeeProduct::getId, cart.getProductId()));
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

            //MySQL 报错抛的是 DataIntegrityViolationException、MyBatisSystemException 等，不是 ServiceException，catch 不住。改成：
        } catch (Exception e) {
            // MySQL 失败 → 回滚 Redis 里已经扣了的库存
            //rollbackStock 方法里判断 if (failedItem != null && ...)，传 null 就不 break，全部回滚。
            rollbackStock(cartItems,null);
            throw e;
        }
    }
    /** 秒杀专用：传秒杀价覆盖商品原价 */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(CreateOrderFrom from, BigDecimal seckillPrice) {
        // 先把秒杀价设到 from 里，或者直接在 createOrder 里用 seckillPrice 覆盖总价
        OrderVO order = createOrder(from);
        // 如果传了秒杀价，覆盖订单总金额
        if (seckillPrice != null) {
            // 更新订单金额为秒杀价
            CoffeeOrder coffeeOrder = orderDao.selectById(order.getId());
            coffeeOrder.setTotalAmount(seckillPrice);
            orderDao.updateById(coffeeOrder);
            order.setTotalAmount(seckillPrice);
        }
        return order;
        /**
         * 这个重载的方法为什么不采用lua脚本
         * 因为它调的是原来的 createOrder(from) 方法，那个方法里已经有 Lua 脚本扣库存的逻辑了。
         * 秒杀下单
         *     │
         *     └─ createOrder(from, seckillPrice)     ← 重载方法
         *             │
         *             └─ createOrder(from)            ← 调原来的方法
         *                     │
         *                     ├─ Redis Lua 扣库存 ✅
         *                     ├─ MySQL 插入订单
         *                     └─ 清空购物车
         *             │
         *             └─ 把订单金额改成秒杀价 ✅
         * Lua 脚本在原方法里，重载方法只是"调用原方法 + 覆盖价格"，没有重复写逻辑。
         */
    }

    public List<OrderVO> listOrders(int page, int size,Integer status) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // SQL: SELECT * FROM coffee_order WHERE user_id = ? ORDER BY create_time DESC LIMIT ?,?
        LambdaQueryWrapper<CoffeeOrder> wrapper = new LambdaQueryWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getUserId, userId);
        if (status != null) {
            wrapper.eq(CoffeeOrder::getStatus, status);
        }
        wrapper.orderByDesc(CoffeeOrder::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);
        List<CoffeeOrder> orders = orderDao.selectList(wrapper);

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
        if (userId == null) throw new ServiceException("请先登录");

        // 1. 查订单
        // SQL: SELECT * FROM coffee_order WHERE id = ?
        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权查看该订单");

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

    public void cancelOrder(Long orderId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // 1. 查订单
        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权操作该订单");

        // 2. 只有待支付才能取消
        if (order.getStatus() != 0) {
            throw new ServiceException("当前订单状态不允许取消");
        }

        // 3. 查订单明细，恢复库存
        List<OrderItem> items = orderItemDao.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        for (OrderItem item : items) {
            productDao.update(null, new LambdaUpdateWrapper<CoffeeProduct>()
                    .setSql("stock = stock + " + item.getQuantity())
                    .eq(CoffeeProduct::getId, item.getProductId()));
        }

        // 4. 更新订单状态为已取消
        order.setStatus(4);
        orderDao.updateById(order);
    }

    public List<AddressVO> listAddresses() {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        List<ShippingAddress> list = addressDao.selectList(
                new LambdaQueryWrapper<ShippingAddress>()
                        .eq(ShippingAddress::getUserId, userId)
                        .orderByDesc(ShippingAddress::getCreateTime)
        );
        return list.stream().map(this::toAddressVO).collect(Collectors.toList());
    }






    // ─── 私有工具 ───────────────────────────────
    private AddressVO toAddressVO(ShippingAddress addr) {
        return AddressVO.builder()
                .id(addr.getId())
                .receiverName(addr.getReceiverName())
                .phone(addr.getPhone())
                .province(addr.getProvince())
                .city(addr.getCity())
                .district(addr.getDistrict())
                .detail(addr.getDetail())
                .isDefault(addr.getIsDefault() == 1)
                .build();
    }

    public AddressVO getAddress(Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        ShippingAddress addr = addressDao.selectById(id);
        if (addr == null) throw new ServiceException("地址不存在");
        if (!addr.getUserId().equals(userId)) throw new ServiceException("无权操作");

        return toAddressVO(addr);
    }

    @Transactional(rollbackFor = Exception.class)
    public AddressVO createAddress(AddressForm form) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // 如果设为默认，先把其他地址的非默认
        if (Boolean.TRUE.equals(form.getIsDefault())) {
            addressDao.update(null, new LambdaUpdateWrapper<ShippingAddress>()
                    .eq(ShippingAddress::getUserId, userId)
                    .set(ShippingAddress::getIsDefault, 0));
        }

        ShippingAddress addr = new ShippingAddress();
        addr.setUserId(userId);
        addr.setReceiverName(form.getReceiverName());
        addr.setPhone(form.getPhone());
        addr.setProvince(form.getProvince());
        addr.setCity(form.getCity());
        addr.setDistrict(form.getDistrict());
        addr.setDetail(form.getDetail());
        addr.setIsDefault(Boolean.TRUE.equals(form.getIsDefault()) ? 1 : 0);
        addr.setCreateTime(LocalDateTime.now());
        addressDao.insert(addr);

        return toAddressVO(addr);
    }

    @Transactional(rollbackFor = Exception.class)
    public AddressVO updateAddress(Long id, AddressForm form) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        ShippingAddress addr = addressDao.selectById(id);
        if (addr == null) throw new ServiceException("地址不存在");
        if (!addr.getUserId().equals(userId)) throw new ServiceException("无权操作");

        // 如果设为默认，先把其他地址的非默认
        if (Boolean.TRUE.equals(form.getIsDefault())) {
            addressDao.update(null, new LambdaUpdateWrapper<ShippingAddress>()
                    .eq(ShippingAddress::getUserId, userId)
                    .set(ShippingAddress::getIsDefault, 0));
        }

        addr.setReceiverName(form.getReceiverName());
        addr.setPhone(form.getPhone());
        addr.setProvince(form.getProvince());
        addr.setCity(form.getCity());
        addr.setDistrict(form.getDistrict());
        addr.setDetail(form.getDetail());
        addr.setIsDefault(Boolean.TRUE.equals(form.getIsDefault()) ? 1 : 0);
        addressDao.updateById(addr);

        return toAddressVO(addr);
    }

    public void deleteAddress(Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        ShippingAddress addr = addressDao.selectById(id);
        if (addr == null) throw new ServiceException("地址不存在");
        if (!addr.getUserId().equals(userId)) throw new ServiceException("无权操作");

        addressDao.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(Long orderId, Integer targetStatus) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // 1. 查订单
        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权操作");

        int current = order.getStatus();

        // 2. 定义允许的状态流转
        // 0(待支付) → 1(已支付)
        // 1(已支付) → 2(已发货)
        // 2(已发货) → 3(已完成)
        boolean allowed = false;
        switch (current) {
            case 0: if (targetStatus == 1) allowed = true; break;
            case 1: if (targetStatus == 2) allowed = true; break;
            case 2: if (targetStatus == 3) allowed = true; break;
            default: break;
        }
        if (!allowed) {
            throw new ServiceException("当前状态不允许流转到目标状态");
        }

        // 3. 更新状态
        order.setStatus(targetStatus);
        orderDao.updateById(order);
        /**
         *    取消订单(cancel) ┄┄┄→ 4(已取消)
         *            ↑
         * 0(待支付) ──→ 1(已支付) ──→ 2(已发货) ──→ 3(已完成)
         *                      ↓
         *                 只有已支付才能继续流转
         */
    }

    // ==================== 商品管理 ====================
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
        product.setStatus(form.getStatus() != null ? form.getStatus() : 1);
        product.setCreateTime(LocalDateTime.now());
        productDao.insert(product);

        // 同步到 ES
        esSearchService.saveProductToEs(product);

        return getProductDetail(product.getId());
    }

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

        // 同步到 ES
        esSearchService.saveProductToEs(product);

        return getProductDetail(product.getId());
    }

    public void deleteProduct(Long productId) {
        CoffeeProduct product = productDao.selectById(productId);
        if (product == null) throw new ServiceException("商品不存在");
        productDao.deleteById(productId);

        // 从 ES 移除
        esSearchService.deleteProductFromEs(productId);

    }

    // ==================== 商品分类 ====================
    public List<CategoryVO> listCategories() {
        List<Category> categories = categoryDao.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder)
        );
        return categories.stream().map(c -> CategoryVO.builder()
                .id(c.getId())
                .name(c.getName())
                .build()).collect(Collectors.toList());
    }


    public CategoryVO createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setCreateTime(LocalDateTime.now());
        categoryDao.insert(category);
        return CategoryVO.builder()
                .id(category.getId()).name(category.getName())
                .build();
    }

    public void deleteCategory(Long id) {
        categoryDao.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long orderId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权操作");
        if (order.getStatus() != 0) throw new ServiceException("当前订单状态不允许支付");

        order.setStatus(1);  // 待支付 → 已支付
        orderDao.updateById(order);
    }



    //=============管理员==============================
    //管理员改库存的接口
    public void updateStock(Long productId, Integer newStock) {
        CoffeeProduct product = productDao.selectById(productId);
        if (product == null) throw new ServiceException("商品不存在");

        product.setStock(newStock);
        productDao.updateById(product);

        // 同步到 Redis
        redisTemplate.opsForValue().set("product:stock:" + productId, String.valueOf(newStock));
        /**最终一致性流程：
         * 下单：
         *     Redis: 10→8 ✅（Lua 原子扣减）
         *     MySQL: 10→8 ✅（同步更新，在一个事务里）
         *
         * 改库存：
         *     MySQL: 20 ✅（先写数据库）
         *     Redis: 20 ✅（同步更新）
         *
         *     任何时候两边的数据都一样，不需要额外补偿。
         *     如果 Redis 挂了重启，启动时 @PostConstruct 会从 MySQL 重新加载库存。
         *
         * @Transactional 的意思是：这个方法里所有的 MySQL 操作，要么全部成功，要么全部失败回滚。
         *     @Transactional 开始
         *     │
         *     ├─ 1. 查购物车
         *     ├─ 2. 校验库存（Redis Lua）
         *     ├─ 3. 生成订单编号
         *     ├─ 4. 插入订单表（MySQL）
         *     ├─ 5. 插入订单明细（MySQL）
         *     ├─ 6. 扣 Redis 库存（Lua）
         *     │
         *     ├─ 7. 同步库存到 MySQL  ← 新增的
         *     │      UPDATE coffee_product SET stock = stock - ?
         *     │      WHERE id = ?
         *     │
         *     ├─ 8. 清空购物车（MySQL）
         *     │
         *     └─ @Transactional 结束 → 提交事务 ✅
         *
         *     如果第 5 步插订单明细失败了：
         *     @Transactional 开始
         *     ├─ 1. ✅
         *     ├─ 2. ✅
         *     ├─ 3. ✅
         *     ├─ 4. ✅
         *     ├─ 5. ❌ 插入失败！
         *     │
         *     └─ @Transactional 回滚
         *             ├─ 4 的订单 → 回滚 ✅
         *             ├─ 7 的库存更新 → 回滚 ✅
         *             └─ 8 的清空购物车 → 回滚 ✅
         *MySQL 这边的事务管得了 MySQL 自己的所有操作。 第 7 步 UPDATE stock = stock - ? 和订单表在同一个事务里，订单失败库存更新也跟着回滚。
         *
         * 那 Redis 那边的库存呢？
         * 事务管不了 Redis。
         * 第 6 步：Redis Lua 扣了库存 10→8 ✅
         * 第 5 步：MySQL 插入订单失败 ❌
         *     │
         *     ├─ MySQL 回滚了订单 ✅
         *     ├─ MySQL 回滚了库存更新（但这一步根本没执行到，因为第 5 步就失败了）
         *     │
         *     └─ Redis 里的库存还是 8 ❌ 没恢复！
         * 所以第 6 步放在 MySQL 操作之前或者之后，都有风险：
         * 顺序	                              风险
         * 先 Redis 后 MySQL	Redis            扣了但 MySQL 失败 → Redis 不归事务管，回滚不了
         * 先 MySQL 后 Redis	MySQL            成功了但 Redis 扣失败 → 人工补偿
         *你现在的代码里第 6 步扣 Redis 库存是在 MySQL 插入订单之后，但仍在 MySQL 事务范围内。如果 MySQL 失败，Redis 的库存不会自动回滚。
         * 所以前面写的 rollbackStock 方法就是干这个用的——在 catch 块里手动恢复 Redis 库存。
         *
         */
    }
}
