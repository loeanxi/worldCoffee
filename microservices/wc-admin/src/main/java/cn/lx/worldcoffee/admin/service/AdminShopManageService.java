package cn.lx.worldcoffee.admin.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.shop.dao.*;
import cn.lx.worldcoffee.module.shop.domain.*;
import cn.lx.worldcoffee.module.shop.domain.from.ProductForm;
import cn.lx.worldcoffee.module.shop.domain.vo.CategoryVO;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminShopManageService {

    private final SysUserDao sysUserDao;
    private final CoffeeProductDao productDao;
    private final CategoryDao categoryDao;
    private final CoffeeOrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final CouponDao couponDao;
    private final CouponProductDao couponProductDao;
    private final UserCouponDao userCouponDao;
    private final LogisticsRecordDao logisticsRecordDao;

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
                .filter(order -> order.getTotalAmount() != null)
                .mapToDouble(order -> order.getTotalAmount().doubleValue())
                .sum();
        stats.put("totalRevenue", totalRevenue);
        return stats;
    }

    public List<ProductVO> listAllProducts(int page, int size, Long categoryId, Integer status) {
        LambdaQueryWrapper<CoffeeProduct> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) wrapper.eq(CoffeeProduct::getCategoryId, categoryId);
        if (status != null) wrapper.eq(CoffeeProduct::getStatus, status);
        wrapper.orderByDesc(CoffeeProduct::getCreateTime).last(limit(page, size));
        return productDao.selectList(wrapper).stream()
                .map(this::toProductVO)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductVO createProduct(ProductForm form) {
        CoffeeProduct product = new CoffeeProduct();
        applyProductForm(product, form);
        product.setSales(form.getSales() != null ? form.getSales() : 0);
        product.setCreateTime(LocalDateTime.now());
        productDao.insert(product);
        return toProductVO(product);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductVO updateProduct(Long id, ProductForm form) {
        CoffeeProduct product = productDao.selectById(id);
        if (product == null) throw new ServiceException("商品不存在");
        applyProductForm(product, form);
        product.setSales(form.getSales() != null ? form.getSales() : product.getSales());
        productDao.updateById(product);
        return toProductVO(product);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        CoffeeProduct product = productDao.selectById(id);
        if (product == null) throw new ServiceException("商品不存在");
        productDao.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleProductStatus(Long productId) {
        CoffeeProduct product = productDao.selectById(productId);
        if (product == null) throw new ServiceException("商品不存在");
        product.setStatus(Objects.equals(product.getStatus(), 1) ? 0 : 1);
        productDao.updateById(product);
    }

    public List<CategoryVO> listCategories() {
        return categoryDao.selectList(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder))
                .stream()
                .map(category -> CategoryVO.builder().id(category.getId()).name(category.getName()).build())
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

    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        categoryDao.deleteById(id);
    }

    public List<OrderVO> listAllOrders(int page, int size, Integer status, Long userId, String orderNo) {
        LambdaQueryWrapper<CoffeeOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(CoffeeOrder::getStatus, status);
        if (userId != null) wrapper.eq(CoffeeOrder::getUserId, userId);
        if (orderNo != null && !orderNo.isBlank()) wrapper.eq(CoffeeOrder::getOrderNo, orderNo.trim());
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
        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!Objects.equals(order.getStatus(), 1)) throw new ServiceException("只有已支付订单可以发货");
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(2);
        order.setShippingCompany(shippingCompany);
        order.setTrackingNo(trackingNo);
        order.setShippedTime(now);
        orderDao.updateById(order);

        LogisticsRecord record = new LogisticsRecord();
        record.setOrderId(orderId);
        record.setStatus("SHIPPED");
        record.setDescription("订单已发货，物流单号：" + trackingNo);
        record.setLocation(shippingCompany);
        record.setCreateTime(now);
        logisticsRecordDao.insert(record);
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
            CouponProduct relation = new CouponProduct();
            relation.setCouponId(couponId);
            relation.setProductId(productId);
            couponProductDao.insert(relation);
        }
    }

    public List<Map<String, Object>> getCouponParticipants(Long couponId) {
        List<UserCoupon> userCoupons = userCouponDao.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, couponId));
        if (userCoupons.isEmpty()) return List.of();

        List<Long> userIds = userCoupons.stream().map(UserCoupon::getUserId).distinct().collect(Collectors.toList());
        Map<Long, SysUser> userMap = sysUserDao.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, user -> user));

        return userCoupons.stream().map(userCoupon -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", userCoupon.getId());
            map.put("userId", userCoupon.getUserId());
            SysUser user = userMap.get(userCoupon.getUserId());
            map.put("username", user != null ? user.getUsername() : "未知用户");
            map.put("used", userCoupon.getUsed());
            map.put("createTime", userCoupon.getCreateTime());
            return map;
        }).collect(Collectors.toList());
    }

    private void applyProductForm(CoffeeProduct product, ProductForm form) {
        product.setName(form.getName());
        product.setDescription(form.getDescription());
        product.setPrice(form.getPrice());
        product.setImages(form.getImages());
        product.setOrigin(form.getOrigin());
        product.setRoastLevel(form.getRoastLevel());
        product.setWeight(form.getWeight());
        product.setStock(form.getStock() != null ? form.getStock() : 0);
        product.setStatus(form.getStatus() != null ? form.getStatus() : 1);
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
