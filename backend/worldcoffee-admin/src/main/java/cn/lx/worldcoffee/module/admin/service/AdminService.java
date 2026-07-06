package cn.lx.worldcoffee.module.admin.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.shop.dao.*;
import cn.lx.worldcoffee.module.shop.domain.*;
import cn.lx.worldcoffee.module.shop.domain.vo.CategoryVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductVO;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.service.LogisticsService;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理后台专用 Service
 *
 * 职责：封装所有管理后台需要的数据查询和操作，
 *       与前台 Service（OrderService 等）的关键区别是不做用户权限校验——
 *       管理员可以查看/操作所有用户的数据。
 *
 * 包含：
 *   - 用户管理：列表、冻结/解冻
 *   - 商品管理：全量列表（含下架）、上下架切换、分类管理
 *   - 订单管理：全量列表（支持多维度筛选）、详情、发货
 *   - 营销管理：优惠券/秒杀活动的增删改查
 *   - 仪表盘：基础统计数据
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final UserDao userDao;
    private final CoffeeProductDao productDao;
    private final CategoryDao categoryDao;
    private final CoffeeOrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final CouponDao couponDao;
    private final CouponProductDao couponProductDao;
    private final UserCouponDao userCouponDao;
    private final LogisticsService logisticsService;

    // ==================== 用户管理 ====================

    /**
     * 用户列表（分页 + 关键词搜索）
     * keyword 模糊匹配用户名和手机号
     */
    public List<Map<String, Object>> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getPhone, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);

        return userDao.selectList(wrapper).stream()
                .map(this::toUserMap).collect(Collectors.toList());
    }

    /** 冻结用户（status 置为 0） */
    @Transactional(rollbackFor = Exception.class)
    public void freezeUser(Long userId) {
        var user = userDao.selectById(userId);
        if (user == null) throw new ServiceException("用户不存在");
        user.setStatus(0);
        userDao.updateById(user);
    }

    /** 解冻用户（status 置为 1） */
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeUser(Long userId) {
        var user = userDao.selectById(userId);
        if (user == null) throw new ServiceException("用户不存在");
        user.setStatus(1);
        userDao.updateById(user);
    }

    private Map<String, Object> toUserMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("phone", u.getPhone());
        m.put("status", u.getStatus());
        m.put("avatar", u.getAvatar());
        m.put("createTime", u.getCreateTime());
        return m;
    }

    // ==================== 商品管理 ====================

    /**
     * 全量商品列表（管理员视角，不过滤状态）
     * categoryId / status 可选过滤
     */
    public List<ProductVO> listAllProducts(int page, int size, Long categoryId, Integer status) {
        LambdaQueryWrapper<CoffeeProduct> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) wrapper.eq(CoffeeProduct::getCategoryId, categoryId);
        if (status != null) wrapper.eq(CoffeeProduct::getStatus, status);
        wrapper.orderByDesc(CoffeeProduct::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);

        return productDao.selectList(wrapper).stream()
                .map(this::toProductVO).collect(Collectors.toList());
    }

    /** 切换商品上下架状态（toggle） */
    @Transactional(rollbackFor = Exception.class)
    public void toggleProductStatus(Long productId) {
        CoffeeProduct product = productDao.selectById(productId);
        if (product == null) throw new ServiceException("商品不存在");
        product.setStatus(product.getStatus() == 1 ? 0 : 1);
        productDao.updateById(product);
    }

    /** 分类列表 */
    public List<CategoryVO> listCategories() {
        return categoryDao.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder))
                .stream().map(c -> CategoryVO.builder()
                        .id(c.getId()).name(c.getName()).build())
                .collect(Collectors.toList());
    }

    /** 新建分类 */
    public CategoryVO createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setCreateTime(LocalDateTime.now());
        categoryDao.insert(category);
        return CategoryVO.builder().id(category.getId()).name(category.getName()).build();
    }

    /** 删除分类 */
    public void deleteCategory(Long id) {
        categoryDao.deleteById(id);
    }

    private ProductVO toProductVO(CoffeeProduct p) {
        return ProductVO.builder()
                .id(p.getId()).name(p.getName()).description(p.getDescription())
                .price(p.getPrice()).images(p.getImages()).origin(p.getOrigin())
                .roastLevel(p.getRoastLevel()).weight(p.getWeight())
                .stock(p.getStock()).sales(p.getSales()).status(p.getStatus())
                .build();
    }

    // ==================== 订单管理 ====================

    /**
     * 全量订单列表（管理员视角，不按 userId 过滤）
     * 支持按订单号、状态、用户ID筛选
     */
    public List<OrderVO> listAllOrders(int page, int size, Integer status, Long userId, String orderNo) {
        LambdaQueryWrapper<CoffeeOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(CoffeeOrder::getStatus, status);
        if (userId != null) wrapper.eq(CoffeeOrder::getUserId, userId);
        if (orderNo != null && !orderNo.isBlank()) wrapper.eq(CoffeeOrder::getOrderNo, orderNo);
        wrapper.orderByDesc(CoffeeOrder::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);

        return orderDao.selectList(wrapper).stream()
                .map(this::toOrderVO).collect(Collectors.toList());
    }

    /** 订单详情（含订单项，管理员不需要校验 userId） */
    public OrderVO getOrderDetail(Long orderId) {
        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");

        List<OrderItem> items = orderItemDao.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        List<OrderVO.OrderItemVO> itemVOs = items.stream().map(i ->
                OrderVO.OrderItemVO.builder()
                        .productId(i.getProductId()).productName(i.getProductName())
                        .price(i.getPrice()).quantity(i.getQuantity()).build())
                .collect(Collectors.toList());

        return OrderVO.builder()
                .id(order.getId()).orderNo(order.getOrderNo()).userId(order.getUserId())
                .totalAmount(order.getTotalAmount()).status(order.getStatus())
                .address(order.getAddress()).remark(order.getRemark())
                .createTime(order.getCreateTime()).items(itemVOs).build();
    }

    /** 发货（委托给 LogisticsService） */
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(Long orderId, String shippingCompany, String trackingNo) {
        logisticsService.shipOrder(orderId, shippingCompany, trackingNo);
    }

    private OrderVO toOrderVO(CoffeeOrder o) {
        return OrderVO.builder()
                .id(o.getId()).orderNo(o.getOrderNo()).userId(o.getUserId())
                .totalAmount(o.getTotalAmount()).status(o.getStatus())
                .address(o.getAddress()).remark(o.getRemark())
                .createTime(o.getCreateTime()).build();
    }

    // ==================== 营销管理（优惠券 + 秒杀活动） ====================

    /**
     * 优惠券列表
     * type: 1-满减券 2-折扣券 3-秒杀券，不传则查全部
     */
    public List<Map<String, Object>> listCoupons(Integer type) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (type != null) wrapper.eq(Coupon::getType, type);
        wrapper.orderByDesc(Coupon::getCreateTime);

        return couponDao.selectList(wrapper).stream()
                .map(this::toCouponMap).collect(Collectors.toList());
    }

    /** 创建优惠券 */
    @Transactional(rollbackFor = Exception.class)
    public Coupon createCoupon(Coupon coupon) {
        coupon.setCreateTime(LocalDateTime.now());
        if (coupon.getStatus() == null) coupon.setStatus(1);
        couponDao.insert(coupon);
        return coupon;
    }

    /** 更新优惠券 */
    @Transactional(rollbackFor = Exception.class)
    public void updateCoupon(Long id, Coupon form) {
        Coupon coupon = couponDao.selectById(id);
        if (coupon == null) throw new ServiceException("优惠券不存在");
        coupon.setName(form.getName());
        coupon.setType(form.getType());
        coupon.setValue(form.getValue());
        coupon.setSeckillPrice(form.getSeckillPrice());
        coupon.setMinAmount(form.getMinAmount());
        coupon.setStock(form.getStock());
        coupon.setStartTime(form.getStartTime());
        coupon.setEndTime(form.getEndTime());
        coupon.setStatus(form.getStatus());
        couponDao.updateById(coupon);
    }

    /** 切换优惠券上下架状态 */
    @Transactional(rollbackFor = Exception.class)
    public void toggleCouponStatus(Long id) {
        Coupon coupon = couponDao.selectById(id);
        if (coupon == null) throw new ServiceException("优惠券不存在");
        coupon.setStatus(coupon.getStatus() == 1 ? 0 : 1);
        couponDao.updateById(coupon);
    }

    /** 删除优惠券 */
    public void deleteCoupon(Long id) {
        couponDao.deleteById(id);
        // 同时清理关联的 coupon_product
        couponProductDao.delete(new LambdaQueryWrapper<CouponProduct>()
                .eq(CouponProduct::getCouponId, id));
    }

    /** 查询秒杀券关联的商品 */
    public List<Long> getCouponProductIds(Long couponId) {
        return couponProductDao.selectList(
                new LambdaQueryWrapper<CouponProduct>().eq(CouponProduct::getCouponId, couponId))
                .stream().map(CouponProduct::getProductId).collect(Collectors.toList());
    }

    /** 设置秒杀券关联商品（先删旧关联，再插新关联） */
    @Transactional(rollbackFor = Exception.class)
    public void setCouponProducts(Long couponId, List<Long> productIds) {
        // 先删旧关联
        couponProductDao.delete(new LambdaQueryWrapper<CouponProduct>()
                .eq(CouponProduct::getCouponId, couponId));
        // 再插新关联
        if (productIds != null) {
            for (Long pid : productIds) {
                CouponProduct cp = new CouponProduct();
                cp.setCouponId(couponId);
                cp.setProductId(pid);
                couponProductDao.insert(cp);
            }
        }
    }

    /** 查看某优惠券被哪些用户领取了 */
    public List<Map<String, Object>> getCouponParticipants(Long couponId) {
        List<UserCoupon> ucs = userCouponDao.selectList(
                new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getCouponId, couponId));
        if (ucs.isEmpty()) return List.of();

        // 批量查用户信息
        List<Long> userIds = ucs.stream().map(UserCoupon::getUserId)
                .distinct().collect(Collectors.toList());
        Map<Long, User> userMap =
                userDao.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        return ucs.stream().map(uc -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", uc.getId());
            m.put("userId", uc.getUserId());
            User u = userMap.get(uc.getUserId());
            m.put("username", u != null ? u.getUsername() : "未知");
            m.put("used", uc.getUsed());
            m.put("createTime", uc.getCreateTime());
            return m;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> toCouponMap(Coupon c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("type", c.getType());
        m.put("value", c.getValue());
        m.put("seckillPrice", c.getSeckillPrice());
        m.put("minAmount", c.getMinAmount());
        m.put("stock", c.getStock());
        m.put("startTime", c.getStartTime());
        m.put("endTime", c.getEndTime());
        m.put("status", c.getStatus());
        m.put("createTime", c.getCreateTime());
        return m;
    }

    // ==================== 仪表盘统计 ====================

    /**
     * 仪表盘基础统计数据
     * 返回：用户总数、商品总数、订单总数、今日订单数、总销售额、待发货数
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 用户总数
        stats.put("userCount", userDao.selectCount(null));

        // 商品总数
        stats.put("productCount", productDao.selectCount(null));

        // 订单总数
        stats.put("orderCount", orderDao.selectCount(null));

        // 今日新增订单
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        stats.put("todayOrderCount", orderDao.selectCount(
                new LambdaQueryWrapper<CoffeeOrder>().ge(CoffeeOrder::getCreateTime, todayStart)));

        // 待发货订单数
        stats.put("pendingShipCount", orderDao.selectCount(
                new LambdaQueryWrapper<CoffeeOrder>().eq(CoffeeOrder::getStatus, 1)));

        // 总销售额（已支付 + 已发货 + 已完成的订单）
        List<CoffeeOrder> paidOrders = orderDao.selectList(
                new LambdaQueryWrapper<CoffeeOrder>().in(CoffeeOrder::getStatus, 1, 2, 3));
        double totalRevenue = paidOrders.stream()
                .mapToDouble(o -> o.getTotalAmount().doubleValue()).sum();
        stats.put("totalRevenue", totalRevenue);

        return stats;
    }
}
