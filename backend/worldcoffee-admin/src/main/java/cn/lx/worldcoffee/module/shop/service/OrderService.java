package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.dao.CartItemDao;
import cn.lx.worldcoffee.module.shop.dao.CoffeeOrderDao;
import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.dao.CouponDao;
import cn.lx.worldcoffee.module.shop.dao.OrderItemDao;
import cn.lx.worldcoffee.module.shop.domain.CartItem;
import cn.lx.worldcoffee.module.shop.domain.CoffeeOrder;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import cn.lx.worldcoffee.module.shop.domain.Coupon;
import cn.lx.worldcoffee.module.shop.domain.OrderItem;
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.util.OrderNoGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单服务 —— 订单全生命周期管理。
 *
 * 职责：
 *   1. 创建订单（购物车下单 + 秒杀下单两个入口）
 *   2. 查询订单（列表、详情、按订单号查）
 *   3. 取消订单（用户主动取消 + 系统超时自动取消）
 *   4. 支付订单（Mock 支付，改状态）
 *   5. 订单状态流转（状态机：0→1→2→3，任何状态→4）
 *
 * 核心流程（createOrder）：
 *   查购物车 → 校验商品/库存 → Redis Lua 原子扣库存 → 插入订单 → 插入订单明细
 *   → MySQL 同步库存（最终一致性）→ 清空购物车 → 返回 OrderVO
 *
 * 为什么单独拆出来：
 *   订单是商城最核心的领域对象，涉及事务、库存扣减、状态机，
 *   跟商品管理、购物车、地址、物流完全不同，不应该混在一个类里。
 *
 * 依赖：
 *   - CoffeeOrderDao：订单表 CRUD
 *   - OrderItemDao：订单明细表 CRUD
 *   - CartItemDao：查购物车、清空购物车
 *   - CoffeeProductDao：批量查商品信息（校验 + 快照）
 *   - InventoryService：Redis Lua 扣库存 + MySQL 同步 + 取消时恢复库存
 *   - OrderNoGenerator：雪花算法生成订单号
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final CoffeeOrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final CartItemDao cartItemDao;
    private final CoffeeProductDao productDao;
    private final InventoryService inventoryService;
    private final OrderNoGenerator orderNoGenerator;
    private final CouponService couponService;
    private final CouponDao couponDao;

    // ==================== 创建订单 ====================

    /**
     * 普通下单：购物车 → 订单。
     *
     * 流程：
     *   1. 查购物车（只查当前用户的）
     *   2. 批量查商品，校验都存在且上架、Redis 库存够
     *   3. 生成订单号（雪花算法）
     *   4. Redis Lua 原子扣库存（高并发不超卖）
     *   5. 插入订单表（MySQL）
     *   6. 批量插入订单明细（快照商品名+价格，防止商品改名后历史订单对不上）
     *   7. MySQL 同步扣库存（最终一致性）
     *   8. 清空购物车
     *
     * 事务说明：
     *   @Transactional 管 MySQL 操作（步骤 5/6/7/8），管不了 Redis。
     *   所以 Redis 扣完之后如果 MySQL 失败，在 catch 里手动回滚 Redis 库存。
     *
     * @param from 包含收货地址、备注、优惠券ID
     * @return 创建好的订单 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(CreateOrderFrom from) {
        Long userId = SecurityUtils.requireUserId();

        // 1. 查购物车
        List<CartItem> cartItems = cartItemDao.selectList(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
        if (cartItems.isEmpty()) throw new ServiceException("购物车是空的");

        // 2. 批量查商品，校验库存
        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId).collect(Collectors.toList());
        Map<Long, CoffeeProduct> productMap = productDao.selectBatchIds(productIds)
                .stream().collect(Collectors.toMap(CoffeeProduct::getId, p -> p));

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cart : cartItems) {
            CoffeeProduct p = productMap.get(cart.getProductId());
            if (p == null || p.getStatus() == 0) {
                throw new ServiceException("商品已下架：" + cart.getProductId());
            }

            // 预检 Redis 库存（Lua 扣减前先看一眼，快速失败）
            if (!inventoryService.checkStock(cart.getProductId(), cart.getQuantity())) {
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

        // 2.5 优惠券折扣
        Long couponId = from.getCouponId();
        BigDecimal discountAmount = BigDecimal.ZERO;
        String couponName = null;
        if (couponId != null) {
            Map<String, Object> couponResult = couponService.applyCoupon(couponId, total);
            discountAmount = (BigDecimal) couponResult.get("discountAmount");
            couponName = (String) couponResult.get("couponName");
            total = total.subtract(discountAmount);
            // 实付金额不能为负
            if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;
        }

        // 3. 生成订单号（雪花算法，全局唯一递增）
        String orderNo = orderNoGenerator.nextOrderNo();

        // 4. Redis Lua 原子扣库存 + 5~8 MySQL 操作
        try {
            // 4. Redis Lua 逐个扣库存
            for (CartItem cart : cartItems) {
                Long result = inventoryService.deductStock(cart.getProductId(), cart.getQuantity());
                if (result == null || result == 0) {
                    // 扣减失败 → 回滚前面已经扣了的 Redis 库存
                    rollbackRedisStock(cartItems, cart);
                    throw new ServiceException("库存不足：" + cart.getProductId());
                }
            }

            // 5. 插入订单
            CoffeeOrder order = new CoffeeOrder();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setTotalAmount(total);
            order.setStatus(0);  // 待支付
            order.setAddress(from.getAddress());
            order.setRemark(from.getRemark());
            order.setCouponId(couponId);
            order.setDiscountAmount(discountAmount);
            order.setCreateTime(LocalDateTime.now());
            orderDao.insert(order);

            // 6. 批量插入订单明细
            for (OrderItem item : orderItems) {
                item.setOrderId(order.getId());
                orderItemDao.insert(item);
            }

            // 7. MySQL 同步扣库存（最终一致性：Redis 已经扣了，MySQL 跟着扣）
            for (CartItem cart : cartItems) {
                inventoryService.syncDeductToMySQL(cart.getProductId(), cart.getQuantity());
            }

            // 8. 清空购物车
            cartItemDao.delete(new LambdaQueryWrapper<CartItem>()
                    .eq(CartItem::getUserId, userId));

            // 9. 组装 VO 返回
            List<OrderVO.OrderItemVO> itemVOs = orderItems.stream().map(i ->
                    OrderVO.OrderItemVO.builder()
                            .productId(i.getProductId())
                            .productName(i.getProductName())
                            .price(i.getPrice())
                            .quantity(i.getQuantity())
                            .build()).collect(Collectors.toList());

            return OrderVO.builder()
                    .id(order.getId())
                    .orderNo(orderNo)
                    .userId(userId)
                    .totalAmount(total)
                    .status(0)
                    .address(from.getAddress())
                    .remark(from.getRemark())
                    .couponId(couponId)
                    .discountAmount(discountAmount)
                    .couponName(couponName)
                    .createTime(order.getCreateTime())
                    .items(itemVOs)
                    .build();

        } catch (Exception e) {
            // MySQL 失败 → 回滚 Redis 里已经扣了的库存
            // 传 null 表示全部回滚（不是"回滚到某个商品之前"）
            rollbackRedisStock(cartItems, null);
            throw e;
        }
    }

    /**
     * 秒杀专用下单：传秒杀价覆盖商品原价计算的总金额。
     *
     * 为什么不单独写一套 Lua：
     *   这个方法调的是上面的 createOrder(from)，里面已经有 Lua 扣库存逻辑了。
     *   秒杀下单只是在普通下单之后，把订单总金额改成秒杀价。
     *
     *   调用链：
     *     createOrder(from, seckillPrice)    ← 秒杀入口
     *         │
     *         └─ createOrder(from)           ← 复用普通下单逻辑
     *                 ├─ Redis Lua 扣库存
     *                 ├─ MySQL 插入订单 + 明细
     *                 └─ 清空购物车
     *         │
     *         └─ 覆盖订单金额为秒杀价
     *
     * @param from         收货地址、备注、优惠券ID
     * @param seckillPrice 秒杀价（不为 null 时覆盖总金额）
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(CreateOrderFrom from, BigDecimal seckillPrice) {
        OrderVO order = createOrder(from);

        if (seckillPrice != null) {
            // 覆盖订单总金额
            CoffeeOrder coffeeOrder = orderDao.selectById(order.getId());
            coffeeOrder.setTotalAmount(seckillPrice);
            orderDao.updateById(coffeeOrder);
            order.setTotalAmount(seckillPrice);
        }
        return order;
    }

    // ==================== 查询订单 ====================

    /**
     * 分页查当前用户的订单列表，可按状态筛选。
     *
     * SQL: SELECT * FROM coffee_order WHERE user_id = ? [AND status = ?]
     *      ORDER BY create_time DESC LIMIT ?, ?
     */
    public List<OrderVO> listOrders(int page, int size, Integer status) {
        Long userId = SecurityUtils.requireUserId();

        LambdaQueryWrapper<CoffeeOrder> wrapper = new LambdaQueryWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getUserId, userId);
        if (status != null) {
            wrapper.eq(CoffeeOrder::getStatus, status);
        }
        wrapper.orderByDesc(CoffeeOrder::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);
        List<CoffeeOrder> orders = orderDao.selectList(wrapper);

        return orders.stream().map(this::toOrderVO).collect(Collectors.toList());
    }

    /**
     * 订单详情（含订单明细）。
     * 校验订单属于当前用户，防止越权查看。
     */
    public OrderVO getOrderDetail(Long orderId) {
        Long userId = SecurityUtils.requireUserId();

        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权查看该订单");

        // 查订单明细
        List<OrderItem> items = orderItemDao.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        return toOrderVOWithItems(order, items);
    }

    /**
     * 按订单号查订单（支付回调、前端轮询支付状态时用）。
     * 不校验用户归属——支付回调场景没有用户上下文。
     */
    public OrderVO getOrderByOrderNo(String orderNo) {
        CoffeeOrder order = orderDao.selectOne(new LambdaQueryWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getOrderNo, orderNo));
        if (order == null) return null;

        List<OrderItem> items = orderItemDao.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));

        return toOrderVOWithItems(order, items);
    }

    // ==================== 取消订单 ====================

    /**
     * 用户主动取消订单。
     * 校验订单属于当前用户。
     */
    public void cancelOrder(Long orderId) {
        Long userId = SecurityUtils.requireUserId();

        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权操作该订单");

        doCancelOrder(orderId, order);
    }

    /**
     * 系统自动取消订单（超时未支付，由延时队列消费者调用）。
     * 不需要校验用户——是系统行为，不是用户操作。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderBySystem(Long orderId) {
        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) {
            log.warn("系统取消订单失败，订单不存在，orderId={}", orderId);
            return;
        }
        doCancelOrder(orderId, order);
    }

    /**
     * 取消订单的核心逻辑（用户取消和系统取消共用）。
     *
     * 做了什么：
     *   1. 校验订单状态必须是"待支付"（已支付/已发货的不能直接取消）
     *   2. 查订单明细，逐个恢复库存（MySQL + Redis）
     *   3. 更新订单状态为"已取消"（status=4）
     */
    private void doCancelOrder(Long orderId, CoffeeOrder order) {
        if (order.getStatus() != 0) {
            throw new ServiceException("当前订单状态不允许取消");
        }

        // 1. 查订单明细
        List<OrderItem> items = orderItemDao.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        // 2. 恢复库存：MySQL + Redis 双恢复
        for (OrderItem item : items) {
            // MySQL 库存回滚：stock = stock + quantity
            inventoryService.restoreStockMySQL(item.getProductId(), item.getQuantity());
            // Redis 库存回滚：increment(key, quantity)
            inventoryService.restoreStockRedis(item.getProductId(), item.getQuantity());
        }

        // 3. 更新订单状态 → 已取消
        order.setStatus(4);
        orderDao.updateById(order);
    }

    // ==================== 支付 ====================

    /**
     * Mock 支付：把订单状态从"待支付"改为"已支付"。
     * 真实场景是第三方支付回调触发，这里简化为直接改状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long orderId) {
        Long userId = SecurityUtils.requireUserId();

        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权操作");
        if (order.getStatus() != 0) throw new ServiceException("当前订单状态不允许支付");

        order.setStatus(1);  // 待支付 → 已支付
        orderDao.updateById(order);
    }

    // ==================== 状态流转 ====================

    /**
     * 订单状态机：控制允许的状态流转方向。
     *
     * 合法流转：
     *   0(待支付) → 1(已支付)     支付
     *   1(已支付) → 2(已发货)     发货
     *   2(已发货) → 3(已完成)     确认收货
     *   0(待支付) → 4(已取消)     取消（走 cancelOrder，不走这里）
     *
     * 状态流转图：
     *   取消订单(cancel) ┄┄┄→ 4(已取消)
     *           ↑
     *   0(待支付) ──→ 1(已支付) ──→ 2(已发货) ──→ 3(已完成)
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(Long orderId, Integer targetStatus) {
        Long userId = SecurityUtils.requireUserId();

        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权操作");

        int current = order.getStatus();

        // 定义允许的状态流转
        boolean allowed = false;
        switch (current) {
            case 0: if (targetStatus == 1) allowed = true; break;  // 待支付 → 已支付
            case 1: if (targetStatus == 2) allowed = true; break;  // 已支付 → 已发货
            case 2: if (targetStatus == 3) allowed = true; break;  // 已发货 → 已完成
            default: break;
        }
        if (!allowed) {
            throw new ServiceException("当前状态不允许流转到目标状态");
        }

        order.setStatus(targetStatus);
        orderDao.updateById(order);
    }

    // ==================== 内部工具 ====================

    /**
     * Redis 库存回滚（下单过程中 Lua 扣了但后续 MySQL 失败时调用）。
     *
     * @param cartItems  本次下单的所有购物车项
     * @param failedItem 扣减失败的那一项（不为 null 时只回滚到它之前，为 null 时全部回滚）
     */
    private void rollbackRedisStock(List<CartItem> cartItems, CartItem failedItem) {
        for (CartItem c : cartItems) {
            if (failedItem != null && c.getProductId().equals(failedItem.getProductId())) break;
            inventoryService.rollbackStock(c.getProductId(), c.getQuantity());
        }
    }

    /**
     * CoffeeOrder → OrderVO（不含明细，列表页用）。
     */
    private OrderVO toOrderVO(CoffeeOrder o) {
        String cName = null;
        if (o.getCouponId() != null) {
            Coupon c = couponDao.selectById(o.getCouponId());
            if (c != null) cName = c.getName();
        }
        return OrderVO.builder()
                .id(o.getId())
                .orderNo(o.getOrderNo())
                .userId(o.getUserId())
                .totalAmount(o.getTotalAmount())
                .status(o.getStatus())
                .address(o.getAddress())
                .remark(o.getRemark())
                .couponId(o.getCouponId())
                .discountAmount(o.getDiscountAmount())
                .couponName(cName)
                .createTime(o.getCreateTime())
                .build();
    }

    /**
     * CoffeeOrder + OrderItem 列表 → OrderVO（含明细，详情页用）。
     */
    private OrderVO toOrderVOWithItems(CoffeeOrder order, List<OrderItem> items) {
        List<OrderVO.OrderItemVO> itemVOs = items.stream().map(i ->
                OrderVO.OrderItemVO.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .price(i.getPrice())
                        .quantity(i.getQuantity())
                        .build()).collect(Collectors.toList());

        String cName = null;
        if (order.getCouponId() != null) {
            Coupon c = couponDao.selectById(order.getCouponId());
            if (c != null) cName = c.getName();
        }

        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .address(order.getAddress())
                .remark(order.getRemark())
                .couponId(order.getCouponId())
                .discountAmount(order.getDiscountAmount())
                .couponName(cName)
                .createTime(order.getCreateTime())
                .items(itemVOs)
                .build();
    }
}
