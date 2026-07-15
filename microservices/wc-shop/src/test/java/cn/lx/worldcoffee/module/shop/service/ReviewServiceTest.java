package cn.lx.worldcoffee.module.shop.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ProductReviewDao reviewDao;
    @Mock private CoffeeOrderDao orderDao;
    @Mock private OrderItemDao orderItemDao;
    @Mock private CoffeeProductDao productDao;
    @Mock private PointService pointService;
    @InjectMocks private ReviewService reviewService;

    @Test
    void submitReview_正常提交() {
        Long userId = 100L;
        CoffeeOrder order = new CoffeeOrder();
        order.setId(1L);
        order.setUserId(userId);
        order.setStatus(3);

        OrderItem item = new OrderItem();
        item.setId(10L);
        item.setOrderId(1L);
        item.setProductId(50L);

        ReviewForm form = new ReviewForm();
        form.setOrderId(1L);
        ReviewForm.ReviewItem reviewItem = new ReviewForm.ReviewItem();
        reviewItem.setOrderItemId(10L);
        reviewItem.setProductId(50L);
        reviewItem.setRating(5);
        reviewItem.setContent("好喝");
        form.setItems(List.of(reviewItem));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(orderDao.selectById(1L)).thenReturn(order);
            when(orderItemDao.selectList(any())).thenReturn(List.of(item));
            when(reviewDao.selectCount(any())).thenReturn(0L);

            reviewService.submitReview(form);

            verify(reviewDao).insert(any(ProductReview.class));
            verify(pointService).earnPointsFromReview(eq(userId), any(), eq(5));
        }
    }

    @Test
    void submitReview_订单不存在_抛异常() {
        ReviewForm form = new ReviewForm();
        form.setOrderId(999L);
        form.setItems(List.of());

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(100L);
            when(orderDao.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> reviewService.submitReview(form))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("订单不存在");
        }
    }

    @Test
    void submitReview_非本人订单_抛异常() {
        Long userId = 100L;
        CoffeeOrder order = new CoffeeOrder();
        order.setId(1L);
        order.setUserId(200L);  // 不同用户
        order.setStatus(3);

        ReviewForm form = new ReviewForm();
        form.setOrderId(1L);
        form.setItems(List.of());

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(orderDao.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> reviewService.submitReview(form))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("无权评价他人订单");
        }
    }

    @Test
    void submitReview_订单未完成_抛异常() {
        Long userId = 100L;
        CoffeeOrder order = new CoffeeOrder();
        order.setId(1L);
        order.setUserId(userId);
        order.setStatus(1);  // 已支付但未完成

        ReviewForm form = new ReviewForm();
        form.setOrderId(1L);
        form.setItems(List.of());

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(orderDao.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> reviewService.submitReview(form))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("订单未完成，不可评价");
        }
    }

    @Test
    void submitReview_订单项已评价_抛异常() {
        Long userId = 100L;
        CoffeeOrder order = new CoffeeOrder();
        order.setId(1L);
        order.setUserId(userId);
        order.setStatus(3);

        OrderItem item = new OrderItem();
        item.setId(10L);
        item.setOrderId(1L);
        item.setProductId(50L);

        ReviewForm form = new ReviewForm();
        form.setOrderId(1L);
        ReviewForm.ReviewItem reviewItem = new ReviewForm.ReviewItem();
        reviewItem.setOrderItemId(10L);
        reviewItem.setProductId(50L);
        reviewItem.setRating(4);
        form.setItems(List.of(reviewItem));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(orderDao.selectById(1L)).thenReturn(order);
            when(orderItemDao.selectList(any())).thenReturn(List.of(item));
            when(reviewDao.selectCount(any())).thenReturn(1L);  // 已存在评价

            assertThatThrownBy(() -> reviewService.submitReview(form))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("订单项已评价");
        }
    }

    @Test
    void getProductReviewSummary_有评价_返回正确聚合() {
        ProductReview r5 = new ProductReview();
        r5.setRating(5);
        ProductReview r4 = new ProductReview();
        r4.setRating(4);
        ProductReview r3 = new ProductReview();
        r3.setRating(3);

        when(reviewDao.selectList(any())).thenReturn(List.of(r5, r4, r3, r5, r5));

        ProductReviewSummaryVO summary = reviewService.getProductReviewSummary(1L);

        assertThat(summary.getTotalCount()).isEqualTo(5);
        assertThat(summary.getAverageRating()).isEqualTo(4.4);
        assertThat(summary.getGoodRate()).isEqualTo(80.0);  // 4/5 = 80%
        assertThat(summary.getRatingDistribution()).containsEntry(5, 3L);
        assertThat(summary.getRatingDistribution()).containsEntry(4, 1L);
        assertThat(summary.getRatingDistribution()).containsEntry(3, 1L);
    }

    @Test
    void getProductReviewSummary_无评价_返回零() {
        when(reviewDao.selectList(any())).thenReturn(List.of());

        ProductReviewSummaryVO summary = reviewService.getProductReviewSummary(1L);

        assertThat(summary.getTotalCount()).isEqualTo(0);
        assertThat(summary.getAverageRating()).isEqualTo(0.0);
        assertThat(summary.getGoodRate()).isEqualTo(0.0);
    }

    @Test
    void replyReview_正常回复() {
        ProductReview review = new ProductReview();
        review.setId(1L);
        review.setAdminReply(null);  // 未回复过
        when(reviewDao.selectById(1L)).thenReturn(review);

        reviewService.replyReview(1L, "感谢评价");

        verify(reviewDao).update(any(), any());
    }

    @Test
    void replyReview_重复回复_抛异常() {
        ProductReview review = new ProductReview();
        review.setId(1L);
        review.setAdminReply("已回复");  // 已回复过
        when(reviewDao.selectById(1L)).thenReturn(review);

        assertThatThrownBy(() -> reviewService.replyReview(1L, "再次回复"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("已回复过该评价");
    }

    @Test
    void replyReview_评价不存在_抛异常() {
        when(reviewDao.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> reviewService.replyReview(999L, "回复"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("评价不存在");
    }
}
