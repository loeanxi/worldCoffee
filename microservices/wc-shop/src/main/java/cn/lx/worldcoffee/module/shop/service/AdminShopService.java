package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.result.Constant;
import cn.lx.worldcoffee.common.security.JwtUtil;
import cn.lx.worldcoffee.module.shop.dao.*;
import cn.lx.worldcoffee.module.shop.domain.*;
import cn.lx.worldcoffee.module.shop.domain.admin.AdminLoginForm;
import cn.lx.worldcoffee.module.shop.domain.admin.AdminLoginVO;
import cn.lx.worldcoffee.module.shop.domain.vo.CategoryVO;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminShopService {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final SysUserDao sysUserDao;
    private final CoffeeProductDao productDao;
    private final CategoryDao categoryDao;
    private final CoffeeOrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final CouponDao couponDao;
    private final CouponProductDao couponProductDao;
    private final UserCouponDao userCouponDao;
    private final ProductService productService;
    private final LogisticsService logisticsService;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:admin123}")
    private String adminPassword;

    public AdminLoginVO login(AdminLoginForm form) {
        if (!adminUsername.equals(form.getUsername()) || !adminPassword.equals(form.getPassword())) {
            throw new ServiceException("管理员账号或密码错误");
        }
        String token = jwtUtil.generateToken(0L, adminUsername);
        return AdminLoginVO.builder().token(token).username(adminUsername).build();
    }

    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return;
        String token = authHeader.substring(7);
        redisTemplate.opsForSet().add("token:blacklist", token);
        redisTemplate.expire("token:blacklist", Constant.JWT_EXPIRATION, TimeUnit.MILLISECONDS);
    }

    public List<ProductVO> listAllProducts(int page, int size, Long categoryId, Integer status) {
        LambdaQueryWrapper<CoffeeProduct> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) wrapper.eq(CoffeeProduct::getCategoryId, categoryId);
        if (status != null) wrapper.eq(CoffeeProduct::getStatus, status);
        wrapper.orderByDesc(CoffeeProduct::getCreateTime).last(limit(page, size));
        return productDao.selectList(wrapper).stream().map(this::toProductVO).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleProductStatus(Long productId) {
        CoffeeProduct product = productDao.selectById(productId);
        if (product == null) throw new ServiceException("商品不存在");
        product.setStatus(Objects.equals(product.getStatus(), 1) ? 0 : 1);
        productDao.updateById(product);
    }

    public ProductVO createProduct(cn.lx.worldcoffee.module.shop.domain.from.ProductForm form) {
        return productService.createProduct(form);
    }

    public ProductVO updateProduct(Long id, cn.lx.worldcoffee.module.shop.domain.from.ProductForm form) {
        return productService.updateProduct(id, form);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }

    public List<CategoryVO> listCategories() {
        return categoryDao.selectList(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder))
                .stream()
                .map(c -> CategoryVO.builder().id(c.getId()).name(c.getName()).build())
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public CategoryVO createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setSortOrder(0);
        category.setCreateTime(LocalDateTime.now());
        categoryDao.insert(category);
        return CategoryVO.builder().id(category.getId()).name(category.getName()).build();
    }

    public void deleteCategory(Long id) {
        categoryDao.deleteById(id);
    }

    public List<OrderVO> listAllOrders(int page, int size, Integer status, Long userId, String orderNo) {
        LambdaQueryWrapper<CoffeeOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(CoffeeOrder::getStatus, status);
        if (userId != null) wrapper.eq(CoffeeOrder::getUserId, userId);
        if (orderNo != null && !orderNo.isBlank()) wrapper.eq(CoffeeOrder::getOrderNo, orderNo);
        wrapper.orderByDesc(CoffeeOrder::getCreateTime).last(limit(page, size));
        return orderDao.selectList(wrapper).stream().map(this::toOrderVO).collect(Collectors.toList());
    }

    public OrderVO getOrderDetail(Long orderId) {
        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        List<OrderVO.OrderItemVO> items = orderItemDao.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId))
                .stream()
                .map(item -> OrderVO.OrderItemVO.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        OrderVO vo = toOrderVO(order);
        vo.setItems(items);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(Long orderId, String shippingCompany, String trackingNo) {
        logisticsService.shipOrder(orderId, shippingCompany, trackingNo);
    }

    public List<Map<String, Object>> listCoupons(Integer type) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (type != null) wrapper.eq(Coupon::getType, type);
        wrapper.orderByDesc(Coupon::getCreateTime);
        return couponDao.selectList(wrapper).stream().map(this::toCouponMap).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Coupon createCoupon(Coupon coupon) {
        coupon.setCreateTime(LocalDateTime.now());
        if (coupon.getStatus() == null) coupon.setStatus(1);
        couponDao.insert(coupon);
        return coupon;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCoupon(Long id, Coupon form) {
        Coupon coupon = couponDao.selectById(id);
        if (coupon == null) throw new ServiceException("优惠券不存在");
        form.setId(id);
        couponDao.updateById(form);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCoupon(Long id) {
        couponDao.deleteById(id);
        couponProductDao.delete(new LambdaQueryWrapper<CouponProduct>().eq(CouponProduct::getCouponId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleCouponStatus(Long id) {
        Coupon coupon = couponDao.selectById(id);
        if (coupon == null) throw new ServiceException("优惠券不存在");
        coupon.setStatus(Objects.equals(coupon.getStatus(), 1) ? 0 : 1);
        couponDao.updateById(coupon);
    }

    public List<Long> getCouponProductIds(Long couponId) {
        return couponProductDao.selectList(new LambdaQueryWrapper<CouponProduct>()
                        .eq(CouponProduct::getCouponId, couponId))
                .stream().map(CouponProduct::getProductId).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void setCouponProducts(Long couponId, List<Long> productIds) {
        couponProductDao.delete(new LambdaQueryWrapper<CouponProduct>().eq(CouponProduct::getCouponId, couponId));
        if (productIds == null) return;
        for (Long productId : productIds) {
            CouponProduct cp = new CouponProduct();
            cp.setCouponId(couponId);
            cp.setProductId(productId);
            couponProductDao.insert(cp);
        }
    }

    public List<Map<String, Object>> getCouponParticipants(Long couponId) {
        List<UserCoupon> userCoupons = userCouponDao.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, couponId));
        if (userCoupons.isEmpty()) return List.of();

        List<Long> userIds = userCoupons.stream().map(UserCoupon::getUserId).distinct().collect(Collectors.toList());
        Map<Long, SysUser> userMap = sysUserDao.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        return userCoupons.stream().map(uc -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", uc.getId());
            map.put("userId", uc.getUserId());
            SysUser user = userMap.get(uc.getUserId());
            map.put("username", user != null ? user.getUsername() : "未知用户");
            map.put("used", uc.getUsed());
            map.put("createTime", uc.getCreateTime());
            return map;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("userCount", sysUserDao.selectCount(null));
        stats.put("productCount", productDao.selectCount(null));
        stats.put("orderCount", orderDao.selectCount(null));

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        stats.put("todayOrderCount", orderDao.selectCount(
                new LambdaQueryWrapper<CoffeeOrder>().ge(CoffeeOrder::getCreateTime, todayStart)));
        stats.put("pendingShipCount", orderDao.selectCount(
                new LambdaQueryWrapper<CoffeeOrder>().eq(CoffeeOrder::getStatus, 1)));

        List<CoffeeOrder> paidOrders = orderDao.selectList(
                new LambdaQueryWrapper<CoffeeOrder>().in(CoffeeOrder::getStatus, 1, 2, 3));
        double totalRevenue = paidOrders.stream()
                .filter(o -> o.getTotalAmount() != null)
                .mapToDouble(o -> o.getTotalAmount().doubleValue())
                .sum();
        stats.put("totalRevenue", totalRevenue);
        return stats;
    }

    private ProductVO toProductVO(CoffeeProduct product) {
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
                .status(product.getStatus())
                .build();
    }

    private OrderVO toOrderVO(CoffeeOrder order) {
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
                .createTime(order.getCreateTime())
                .build();
    }

    private Map<String, Object> toCouponMap(Coupon coupon) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", coupon.getId());
        map.put("name", coupon.getName());
        map.put("type", coupon.getType());
        map.put("value", coupon.getValue());
        map.put("seckillPrice", coupon.getSeckillPrice());
        map.put("minAmount", coupon.getMinAmount());
        map.put("stock", coupon.getStock());
        map.put("startTime", coupon.getStartTime());
        map.put("endTime", coupon.getEndTime());
        map.put("status", coupon.getStatus());
        map.put("createTime", coupon.getCreateTime());
        return map;
    }

    private String limit(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(Math.min(size, 100), 1);
        return "LIMIT " + ((safePage - 1) * safeSize) + "," + safeSize;
    }
}
