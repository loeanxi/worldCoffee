package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.module.shop.dao.*;
import cn.lx.worldcoffee.module.shop.domain.*;
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShopServiceTest {

    @InjectMocks
    private ShopService shopService;

    @Mock private CoffeeProductDao productDao;
    @Mock private CartItemDao cartItemDao;
    @Mock private CoffeeOrderDao orderDao;
    @Mock private OrderItemDao orderItemDao;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("1");
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void createOrder_shouldCreateOrderAndClearCart() {
        CartItem item = new CartItem();
        item.setId(1L);
        item.setUserId(1L);
        item.setProductId(10L);
        item.setQuantity(2);
        when(cartItemDao.selectList(any())).thenReturn(List.of(item));

        CoffeeProduct product = new CoffeeProduct();
        product.setId(10L);
        product.setName("耶加雪菲");
        product.setPrice(new BigDecimal("68.00"));
        product.setStock(10);
        product.setStatus(1);
        when(productDao.selectBatchIds(List.of(10L))).thenReturn(List.of(product));
        when(valueOps.get(anyString())).thenReturn("10");
        when(redisTemplate.execute(any(), any(), any())).thenReturn(1L);

        CreateOrderFrom form = new CreateOrderFrom();
        form.setAddress("北京市");
        form.setRemark("测试");
        shopService.createOrder(form);

        verify(orderDao, times(1)).insert(any(CoffeeOrder.class));
        verify(cartItemDao, times(1)).delete(any());
    }

    @Test
    void createOrder_shouldThrowWhenCartEmpty() {
        when(cartItemDao.selectList(any())).thenReturn(List.of());

        CreateOrderFrom form = new CreateOrderFrom();
        form.setAddress("北京");

        Exception e = assertThrows(RuntimeException.class, () -> shopService.createOrder(form));
        assertTrue(e.getMessage().contains("空"));
    }

    @Test
    void createOrder_shouldThrowWhenStockNotEnough() {
        CartItem item = new CartItem();
        item.setUserId(1L);
        item.setProductId(10L);
        item.setQuantity(99);
        when(cartItemDao.selectList(any())).thenReturn(List.of(item));

        CoffeeProduct product = new CoffeeProduct();
        product.setId(10L);
        product.setName("耶加雪菲");
        product.setPrice(new BigDecimal("68.00"));
        product.setStock(10);
        product.setStatus(1);
        when(productDao.selectBatchIds(List.of(10L))).thenReturn(List.of(product));
        when(valueOps.get(anyString())).thenReturn("10");
        when(redisTemplate.execute(any(), any(), any())).thenReturn(0L);

        Exception e = assertThrows(ServiceException.class, () -> {
            CreateOrderFrom form = new CreateOrderFrom();
            form.setAddress("北京");
            shopService.createOrder(form);
        });
        assertTrue(e.getMessage().contains("库存不足"));
    }
}
