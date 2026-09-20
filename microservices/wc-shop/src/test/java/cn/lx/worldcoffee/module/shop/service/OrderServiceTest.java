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
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.module.shop.util.OrderNoGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest extends MpTableInfoSupport {

    @Mock private CoffeeOrderDao orderDao;
    @Mock private OrderItemDao orderItemDao;
    @Mock private CartItemDao cartItemDao;
    @Mock private CoffeeProductDao productDao;
    @Mock private CouponDao couponDao;
    @Mock private OrderNoGenerator orderNoGenerator;
    @Mock private InventoryService inventoryService;
    @Mock private CouponService couponService;
    @InjectMocks private OrderService orderService;

    // ─── 创建订单边界测试 ─────────────────────────────────

    @Test
    void createOrder_购物车为空_抛异常() {
        CreateOrderFrom from = new CreateOrderFrom();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(100L);
            when(cartItemDao.selectList(any())).thenReturn(List.of());

            assertThatThrownBy(() -> orderService.createOrder(from))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("购物车是空的");
        }
    }

    @Test
    void createOrder_商品已下架_抛异常() {
        Long userId = 100L;
        CartItem cart = new CartItem();
        cart.setProductId(1L);
        cart.setQuantity(2);

        CoffeeProduct product = new CoffeeProduct();
        product.setId(1L);
        product.setStatus(0);  // 已下架
        product.setPrice(new BigDecimal("25.00"));

        CreateOrderFrom from = new CreateOrderFrom();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(cartItemDao.selectList(any())).thenReturn(List.of(cart));
            when(productDao.selectBatchIds(any())).thenReturn(List.of(product));

            assertThatThrownBy(() -> orderService.createOrder(from))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("商品已下架");
        }
    }

    @Test
    void createOrder_库存不足_抛异常() {
        Long userId = 100L;
        CartItem cart = new CartItem();
        cart.setProductId(1L);
        cart.setQuantity(999);  // 超量

        CoffeeProduct product = new CoffeeProduct();
        product.setId(1L);
        product.setStatus(1);
        product.setPrice(new BigDecimal("25.00"));

        CreateOrderFrom from = new CreateOrderFrom();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(cartItemDao.selectList(any())).thenReturn(List.of(cart));
            when(productDao.selectBatchIds(any())).thenReturn(List.of(product));
            when(inventoryService.checkStock(1L, 999)).thenReturn(false);

            assertThatThrownBy(() -> orderService.createOrder(from))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("库存不足");
        }
    }

    // ─── 取消订单边界测试 ─────────────────────────────────

    @Test
    void cancelOrder_订单不存在_抛异常() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(100L);
            when(orderDao.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.cancelOrder(999L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("订单不存在");
        }
    }

    @Test
    void cancelOrder_非本人订单_抛异常() {
        Long userId = 100L;
        CoffeeOrder order = new CoffeeOrder();
        order.setId(1L);
        order.setUserId(200L);  // 不同用户
        order.setStatus(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(orderDao.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("无权操作该订单");
        }
    }

    @Test
    void cancelOrder_已支付订单不可取消_抛异常() {
        Long userId = 100L;
        CoffeeOrder order = new CoffeeOrder();
        order.setId(1L);
        order.setUserId(userId);
        order.setStatus(1);  // 已支付

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(orderDao.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("当前状态不允许取消");
        }
    }

    // ─── 订单状态流转边界测试 ─────────────────────────────

    @Test
    void updateOrderStatus_订单不存在_抛异常() {
        when(orderDao.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.updateOrderStatus(999L, 1))
                .isInstanceOf(ServiceException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void updateOrderStatus_非法状态流转_抛异常() {
        CoffeeOrder order = new CoffeeOrder();
        order.setId(1L);
        order.setStatus(0);  // 待支付

        when(orderDao.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.updateOrderStatus(1L, 3))  // 待支付 → 已完成，不允许
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前状态不允许流转到目标状态");
    }

    // ─── 查看订单边界测试 ─────────────────────────────────

    @Test
    void getOrderDetail_非本人订单_抛异常() {
        Long userId = 100L;
        CoffeeOrder order = new CoffeeOrder();
        order.setId(1L);
        order.setUserId(200L);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(orderDao.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.getOrderDetail(1L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("无权查看该订单");
        }
    }

    // ─── 库存补偿（Redis/MySQL 最终一致性）边界测试 ─────────────

    /**
     * 并发/超卖防护的关键路径：第二件商品 Redis 扣减失败时，
     * 必须回滚第一件已经扣掉的 Redis 库存，失败项自身不扣不回滚。
     */
    @Test
    void createOrder_Redis扣减失败_回滚已扣库存() {
        Long userId = 100L;
        CartItem cart1 = new CartItem();
        cart1.setProductId(1L);
        cart1.setQuantity(1);
        CartItem cart2 = new CartItem();
        cart2.setProductId(2L);
        cart2.setQuantity(1);

        CoffeeProduct p1 = new CoffeeProduct();
        p1.setId(1L);
        p1.setStatus(1);
        p1.setPrice(new BigDecimal("10.00"));
        CoffeeProduct p2 = new CoffeeProduct();
        p2.setId(2L);
        p2.setStatus(1);
        p2.setPrice(new BigDecimal("20.00"));

        CreateOrderFrom from = new CreateOrderFrom();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(cartItemDao.selectList(any())).thenReturn(List.of(cart1, cart2));
            when(productDao.selectBatchIds(any())).thenReturn(List.of(p1, p2));
            when(inventoryService.checkStock(anyLong(), anyInt())).thenReturn(true);
            when(orderNoGenerator.nextOrderNo()).thenReturn("TEST-NO-1");
            // 第一件扣成功，第二件扣失败（0 = 库存不足）
            when(inventoryService.deductStock(1L, 1)).thenReturn(1L);
            when(inventoryService.deductStock(2L, 1)).thenReturn(0L);

            assertThatThrownBy(() -> orderService.createOrder(from))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("库存不足");

            // 只回滚第一件；失败项自身没有扣减，不应回滚
            verify(inventoryService).rollbackStock(1L, 1);
            verify(inventoryService, never()).rollbackStock(eq(2L), anyInt());
            // 订单不应落库
            verify(orderDao, never()).insert(any(CoffeeOrder.class));
        }
    }

    /**
     * MySQL 写库失败时（事务管不到 Redis），catch 块必须全量回滚 Redis 库存，
     * 并把原始异常继续抛出由事务回滚 MySQL。
     */
    @Test
    void createOrder_MySQL异常_回滚全部Redis库存() {
        Long userId = 100L;
        CartItem cart1 = new CartItem();
        cart1.setProductId(1L);
        cart1.setQuantity(1);

        CoffeeProduct p1 = new CoffeeProduct();
        p1.setId(1L);
        p1.setStatus(1);
        p1.setPrice(new BigDecimal("10.00"));

        CreateOrderFrom from = new CreateOrderFrom();
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(cartItemDao.selectList(any())).thenReturn(List.of(cart1));
            when(productDao.selectBatchIds(any())).thenReturn(List.of(p1));
            when(inventoryService.checkStock(anyLong(), anyInt())).thenReturn(true);
            when(orderNoGenerator.nextOrderNo()).thenReturn("TEST-NO-2");
            when(inventoryService.deductStock(1L, 1)).thenReturn(1L);
            // MySQL 插入订单失败（模拟连接中断）
            doThrow(new RuntimeException("DB connection lost")).when(orderDao).insert(any(CoffeeOrder.class));

            assertThatThrownBy(() -> orderService.createOrder(from))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB connection lost");

            // 全量回滚 Redis 库存
            verify(inventoryService).rollbackStock(1L, 1);
        }
    }
}
