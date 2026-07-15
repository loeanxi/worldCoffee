package cn.lx.worldcoffee.module.shop.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.dao.CoffeeOrderDao;
import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.dao.OrderItemDao;
import cn.lx.worldcoffee.module.shop.dao.ProductReviewDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeOrder;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import cn.lx.worldcoffee.module.shop.domain.OrderItem;
import cn.lx.worldcoffee.module.shop.domain.ProductReview;
import cn.lx.worldcoffee.module.shop.domain.from.ReviewForm;
import cn.lx.worldcoffee.module.shop.domain.vo.ProductReviewSummaryVO;
import cn.lx.worldcoffee.module.shop.domain.vo.ReviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品评价服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ProductReviewDao reviewDao;
    private final CoffeeOrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final CoffeeProductDao productDao;
    @Lazy
    private final PointService pointService;

    /**
     * 提交评价
     * 校验：订单存在 + 属于当前用户 + 订单状态为已完成(3) + 每个订单项未评价过
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(ReviewForm form) {
        Long userId = SecurityUtils.requireUserId();

        // 1. 查订单，校验归属和状态
        CoffeeOrder order = orderDao.selectById(form.getOrderId());
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权评价他人订单");
        if (order.getStatus() != 3) throw new ServiceException("订单未完成，不可评价");

        // 2. 查订单项，校验归属
        List<OrderItem> orderItems = orderItemDao.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        Map<Long, OrderItem> itemMap = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, i -> i));

        // 3. 逐条插入评价
        for (ReviewForm.ReviewItem item : form.getItems()) {
            OrderItem orderItem = itemMap.get(item.getOrderItemId());
            if (orderItem == null) throw new ServiceException("订单项不属于该订单");

            // 幂等校验：一个订单项只能评价一次
            Long existCount = reviewDao.selectCount(new LambdaQueryWrapper<ProductReview>()
                    .eq(ProductReview::getOrderItemId, item.getOrderItemId()));
            if (existCount > 0) throw new ServiceException("订单项已评价");

            ProductReview review = new ProductReview();
            review.setOrderId(order.getId());
            review.setOrderItemId(item.getOrderItemId());
            review.setProductId(item.getProductId());
            review.setUserId(userId);
            review.setRating(item.getRating());
            review.setContent(item.getContent());
            review.setImages(item.getImages() != null ? JSONUtil.toJsonStr(item.getImages()) : null);
            review.setIsAnonymous(Boolean.TRUE.equals(item.getAnonymous()) ? 1 : 0);
            review.setStatus(1);
            review.setCreateTime(LocalDateTime.now());
            reviewDao.insert(review);

            // 评价奖励积分
            pointService.earnPointsFromReview(userId, review.getId(), item.getRating());
        }
    }

    /**
     * 商品评价列表（分页）
     */
    public List<ReviewVO> listProductReviews(Long productId, int page, int size) {
        List<ProductReview> reviews = reviewDao.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getProductId, productId)
                        .eq(ProductReview::getStatus, 1)
                        .orderByDesc(ProductReview::getCreateTime)
                        .last("LIMIT " + (page - 1) * size + "," + size));

        if (reviews.isEmpty()) return List.of();

        // 查商品名
        CoffeeProduct product = productDao.selectById(productId);
        String productName = product != null ? product.getName() : "未知商品";

        return reviews.stream().map(r -> ReviewVO.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .username(r.getIsAnonymous() == 1 ? "匿名用户" : "用户" + r.getUserId())
                .productId(r.getProductId())
                .productName(productName)
                .rating(r.getRating())
                .content(r.getContent())
                .images(r.getImages() != null ? JSONUtil.toList(r.getImages(), String.class) : List.of())
                .adminReply(r.getAdminReply())
                .adminReplyTime(r.getAdminReplyTime())
                .createTime(r.getCreateTime())
                .build()).collect(Collectors.toList());
    }

    /**
     * 商品评分聚合（详情页展示）
     */
    public ProductReviewSummaryVO getProductReviewSummary(Long productId) {
        List<ProductReview> reviews = reviewDao.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getProductId, productId)
                        .eq(ProductReview::getStatus, 1));

        if (reviews.isEmpty()) {
            return ProductReviewSummaryVO.builder()
                    .productId(productId)
                    .averageRating(0.0)
                    .totalCount(0L)
                    .ratingDistribution(Map.of())
                    .goodRate(0.0)
                    .build();
        }

        double avg = reviews.stream().mapToInt(ProductReview::getRating).average().orElse(0);
        Map<Integer, Long> dist = reviews.stream()
                .collect(Collectors.groupingBy(ProductReview::getRating, Collectors.counting()));
        long goodCount = reviews.stream().filter(r -> r.getRating() >= 4).count();

        return ProductReviewSummaryVO.builder()
                .productId(productId)
                .averageRating(Math.round(avg * 10) / 10.0)
                .totalCount((long) reviews.size())
                .ratingDistribution(dist)
                .goodRate(Math.round((double) goodCount / reviews.size() * 1000) / 10.0)
                .build();
    }

    /**
     * 商家回复评价
     */
    @Transactional(rollbackFor = Exception.class)
    public void replyReview(Long reviewId, String reply) {
        ProductReview review = reviewDao.selectById(reviewId);
        if (review == null) throw new ServiceException("评价不存在");
        if (review.getAdminReply() != null) throw new ServiceException("已回复过该评价");

        reviewDao.update(null, new LambdaUpdateWrapper<ProductReview>()
                .eq(ProductReview::getId, reviewId)
                .set(ProductReview::getAdminReply, reply)
                .set(ProductReview::getAdminReplyTime, LocalDateTime.now()));
    }

    /**
     * 我的评价列表
     */
    public List<ReviewVO> myReviews(int page, int size) {
        Long userId = SecurityUtils.requireUserId();
        List<ProductReview> reviews = reviewDao.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getUserId, userId)
                        .orderByDesc(ProductReview::getCreateTime)
                        .last("LIMIT " + (page - 1) * size + "," + size));

        return reviews.stream().map(r -> ReviewVO.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .productId(r.getProductId())
                .rating(r.getRating())
                .content(r.getContent())
                .images(r.getImages() != null ? JSONUtil.toList(r.getImages(), String.class) : List.of())
                .adminReply(r.getAdminReply())
                .createTime(r.getCreateTime())
                .build()).collect(Collectors.toList());
    }
}
