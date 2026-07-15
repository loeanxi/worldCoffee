package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.dao.CoffeeOrderDao;
import cn.lx.worldcoffee.module.shop.dao.PaymentRecordDao;
import cn.lx.worldcoffee.module.shop.dao.RefundRecordDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeOrder;
import cn.lx.worldcoffee.module.shop.domain.PaymentRecord;
import cn.lx.worldcoffee.module.shop.domain.RefundRecord;
import cn.lx.worldcoffee.module.shop.domain.from.RefundApplyForm;
import cn.lx.worldcoffee.module.shop.domain.vo.RefundVO;
import cn.lx.worldcoffee.module.shop.util.OrderNoGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private RefundRecordDao refundDao;
    @Mock private CoffeeOrderDao orderDao;
    @Mock private PaymentRecordDao paymentDao;
    @Mock private OrderNoGenerator orderNoGenerator;
    @InjectMocks private RefundService refundService;

    @Test
    void applyRefund_正常申请() {
        Long userId = 100L;
        CoffeeOrder order = new CoffeeOrder();
        order.setOrderNo("ORD123");
        order.setUserId(userId);
        order.setStatus(1);  // 已支付
        order.setRefundStatus(0);

        PaymentRecord payment = new PaymentRecord();
        payment.setOrderNo("ORD123");
        payment.setStatus(1);
        payment.setAmount(new BigDecimal("99.50"));

        RefundApplyForm form = new RefundApplyForm();
        form.setOrderNo("ORD123");
        form.setType(1);
        form.setReason("商品有质量问题");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(orderDao.selectOne(any())).thenReturn(order);
            when(paymentDao.selectOne(any())).thenReturn(payment);
            when(orderNoGenerator.nextOrderNo()).thenReturn("1234567890");

            RefundVO result = refundService.applyRefund(form);

            assertThat(result).isNotNull();
            assertThat(result.getRefundNo()).startsWith("RF");
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("99.50"));
            assertThat(result.getStatus()).isEqualTo(0);
            verify(refundDao).insert(any(RefundRecord.class));
            verify(orderDao).update(any(), any());  // 更新 refundStatus
        }
    }

    @Test
    void applyRefund_订单不存在_抛异常() {
        RefundApplyForm form = new RefundApplyForm();
        form.setOrderNo("NOTEXIST");
        form.setType(1);
        form.setReason("test");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(100L);
            when(orderDao.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> refundService.applyRefund(form))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("订单不存在");
        }
    }

    @Test
    void applyRefund_非本人订单_抛异常() {
        CoffeeOrder order = new CoffeeOrder();
        order.setOrderNo("ORD123");
        order.setUserId(200L);  // 不同用户
        order.setStatus(1);

        RefundApplyForm form = new RefundApplyForm();
        form.setOrderNo("ORD123");
        form.setType(1);
        form.setReason("test");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(100L);
            when(orderDao.selectOne(any())).thenReturn(order);

            assertThatThrownBy(() -> refundService.applyRefund(form))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("无权操作他人订单");
        }
    }

    @Test
    void applyRefund_订单未支付_抛异常() {
        CoffeeOrder order = new CoffeeOrder();
        order.setOrderNo("ORD123");
        order.setUserId(100L);
        order.setStatus(0);  // 待支付

        RefundApplyForm form = new RefundApplyForm();
        form.setOrderNo("ORD123");
        form.setType(1);
        form.setReason("test");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(100L);
            when(orderDao.selectOne(any())).thenReturn(order);

            assertThatThrownBy(() -> refundService.applyRefund(form))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("订单未支付，无需退款");
        }
    }

    @Test
    void applyRefund_已有活跃退款_抛异常() {
        CoffeeOrder order = new CoffeeOrder();
        order.setOrderNo("ORD123");
        order.setUserId(100L);
        order.setStatus(1);
        order.setRefundStatus(1);  // 退款中

        RefundApplyForm form = new RefundApplyForm();
        form.setOrderNo("ORD123");
        form.setType(1);
        form.setReason("test");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(100L);
            when(orderDao.selectOne(any())).thenReturn(order);

            assertThatThrownBy(() -> refundService.applyRefund(form))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("订单已有退款申请处理中");
        }
    }

    @Test
    void auditRefund_审核通过() {
        RefundRecord refund = new RefundRecord();
        refund.setId(1L);
        refund.setRefundNo("RF123");
        refund.setOrderNo("ORD123");
        refund.setUserId(100L);
        refund.setStatus(0);  // 申请中

        when(refundDao.selectOne(any())).thenReturn(refund);

        refundService.auditRefund("RF123", true, "同意退款");

        // 验证退款记录更新
        verify(refundDao).update(any(), any());
        // 验证订单退款状态更新 + 订单状态改4
        verify(orderDao).update(any(), any());
        // 验证支付记录更新
        verify(paymentDao).update(any(), any());
    }

    @Test
    void auditRefund_审核拒绝() {
        RefundRecord refund = new RefundRecord();
        refund.setId(1L);
        refund.setRefundNo("RF123");
        refund.setOrderNo("ORD123");
        refund.setStatus(0);

        when(refundDao.selectOne(any())).thenReturn(refund);

        refundService.auditRefund("RF123", false, "不符合退款条件");

        verify(refundDao).update(any(), any());
        verify(orderDao).update(any(), any());  // refundStatus 恢复0
        verify(paymentDao, never()).update(any(), any());  // 不改支付记录
    }

    @Test
    void auditRefund_退款已处理不可再审_抛异常() {
        RefundRecord refund = new RefundRecord();
        refund.setId(1L);
        refund.setRefundNo("RF123");
        refund.setStatus(2);  // 已退款成功

        when(refundDao.selectOne(any())).thenReturn(refund);

        assertThatThrownBy(() -> refundService.auditRefund("RF123", true, ""))
                .isInstanceOf(ServiceException.class)
                .hasMessage("退款单状态不允许审核");
    }

    @Test
    void cancelRefund_正常取消() {
        Long userId = 100L;
        RefundRecord refund = new RefundRecord();
        refund.setId(1L);
        refund.setRefundNo("RF123");
        refund.setUserId(userId);
        refund.setStatus(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(refundDao.selectOne(any())).thenReturn(refund);

            refundService.cancelRefund("RF123");

            verify(refundDao).update(any(), any());
            verify(orderDao).update(any(), any());
        }
    }

    @Test
    void cancelRefund_退款已成功不可取消_抛异常() {
        Long userId = 100L;
        RefundRecord refund = new RefundRecord();
        refund.setId(1L);
        refund.setRefundNo("RF123");
        refund.setUserId(userId);
        refund.setStatus(2);  // 退款成功

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(refundDao.selectOne(any())).thenReturn(refund);

            assertThatThrownBy(() -> refundService.cancelRefund("RF123"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("退款已处理，不可取消");
        }
    }
}
