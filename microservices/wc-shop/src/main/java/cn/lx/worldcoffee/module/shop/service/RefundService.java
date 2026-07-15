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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 退款服务
 * 状态机：0申请中 → 1审核中 → 2退款成功 / 3退款拒绝 / 4已取消
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRecordDao refundDao;
    private final CoffeeOrderDao orderDao;
    private final PaymentRecordDao paymentDao;
    private final OrderNoGenerator orderNoGenerator;

    private static final String[] TYPE_DESCS = {"", "仅退款", "退货退款"};
    private static final String[] STATUS_DESCS = {"申请中", "审核中", "退款成功", "退款拒绝", "已取消"};

    /**
     * 申请退款
     */
    @Transactional(rollbackFor = Exception.class)
    public RefundVO applyRefund(RefundApplyForm form) {
        Long userId = SecurityUtils.requireUserId();

        CoffeeOrder order = orderDao.selectOne(new LambdaQueryWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getOrderNo, form.getOrderNo()));
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权操作他人订单");
        if (order.getStatus() == 0) throw new ServiceException("订单未支付，无需退款");
        if (order.getStatus() == 4) throw new ServiceException("订单已取消");
        if (order.getRefundStatus() != null && order.getRefundStatus() == 1)
            throw new ServiceException("订单已有退款申请处理中");

        // 查支付记录确认退款金额
        PaymentRecord payment = paymentDao.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderNo, form.getOrderNo())
                .eq(PaymentRecord::getStatus, 1));
        if (payment == null) throw new ServiceException("未找到成功的支付记录");

        RefundRecord refund = new RefundRecord();
        refund.setRefundNo("RF" + orderNoGenerator.nextOrderNo());
        refund.setOrderNo(form.getOrderNo());
        refund.setUserId(userId);
        refund.setType(form.getType());
        refund.setReason(form.getReason());
        refund.setAmount(payment.getAmount());
        refund.setStatus(0);
        refund.setTrackingNo(form.getTrackingNo());
        refund.setCreateTime(LocalDateTime.now());
        refundDao.insert(refund);

        // 更新订单退款状态
        orderDao.update(null, new LambdaUpdateWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getOrderNo, form.getOrderNo())
                .set(CoffeeOrder::getRefundStatus, 1));

        return toVO(refund);
    }

    /**
     * 审核退款（admin）
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditRefund(String refundNo, boolean approved, String remark) {
        RefundRecord refund = refundDao.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getRefundNo, refundNo));
        if (refund == null) throw new ServiceException("退款单不存在");
        if (refund.getStatus() != 0 && refund.getStatus() != 1)
            throw new ServiceException("退款单状态不允许审核");

        int newStatus = approved ? 2 : 3;
        refundDao.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, refund.getId())
                .set(RefundRecord::getStatus, newStatus)
                .set(RefundRecord::getAdminRemark, remark)
                .set(RefundRecord::getHandleTime, LocalDateTime.now()));

        if (approved) {
            // 退款成功：订单退款状态→2，订单状态→4(已取消)
            orderDao.update(null, new LambdaUpdateWrapper<CoffeeOrder>()
                    .eq(CoffeeOrder::getOrderNo, refund.getOrderNo())
                    .set(CoffeeOrder::getRefundStatus, 2)
                    .set(CoffeeOrder::getStatus, 4));

            // 支付记录标记为已退款
            paymentDao.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                    .eq(PaymentRecord::getOrderNo, refund.getOrderNo())
                    .eq(PaymentRecord::getStatus, 1)
                    .set(PaymentRecord::getStatus, 2));
        } else {
            // 退款拒绝：恢复订单退款状态
            orderDao.update(null, new LambdaUpdateWrapper<CoffeeOrder>()
                    .eq(CoffeeOrder::getOrderNo, refund.getOrderNo())
                    .set(CoffeeOrder::getRefundStatus, 0));
        }
    }

    /**
     * 用户取消退款申请
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelRefund(String refundNo) {
        Long userId = SecurityUtils.requireUserId();
        RefundRecord refund = refundDao.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getRefundNo, refundNo));
        if (refund == null) throw new ServiceException("退款单不存在");
        if (!refund.getUserId().equals(userId)) throw new ServiceException("无权操作");
        if (refund.getStatus() >= 2) throw new ServiceException("退款已处理，不可取消");

        refundDao.update(null, new LambdaUpdateWrapper<RefundRecord>()
                .eq(RefundRecord::getId, refund.getId())
                .set(RefundRecord::getStatus, 4));

        orderDao.update(null, new LambdaUpdateWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getOrderNo, refund.getOrderNo())
                .set(CoffeeOrder::getRefundStatus, 0));
    }

    /**
     * 我的退款列表
     */
    public List<RefundVO> myRefunds(int page, int size) {
        Long userId = SecurityUtils.requireUserId();
        List<RefundRecord> refunds = refundDao.selectList(
                new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getUserId, userId)
                        .orderByDesc(RefundRecord::getCreateTime)
                        .last("LIMIT " + (page - 1) * size + "," + size));
        return refunds.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 按订单号查退款
     */
    public RefundVO getByOrderNo(String orderNo) {
        RefundRecord refund = refundDao.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getOrderNo, orderNo)
                .orderByDesc(RefundRecord::getCreateTime)
                .last("LIMIT 1"));
        return refund != null ? toVO(refund) : null;
    }

    /**
     * admin 退款列表
     */
    public List<RefundVO> listRefunds(Integer status, int page, int size) {
        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<RefundRecord>()
                .orderByDesc(RefundRecord::getCreateTime);
        if (status != null) wrapper.eq(RefundRecord::getStatus, status);
        wrapper.last("LIMIT " + (page - 1) * size + "," + size);

        return refundDao.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    private RefundVO toVO(RefundRecord r) {
        return RefundVO.builder()
                .id(r.getId())
                .refundNo(r.getRefundNo())
                .orderNo(r.getOrderNo())
                .type(r.getType())
                .typeDesc(r.getType() >= 1 && r.getType() <= 2 ? TYPE_DESCS[r.getType()] : "未知")
                .reason(r.getReason())
                .amount(r.getAmount())
                .status(r.getStatus())
                .statusDesc(r.getStatus() >= 0 && r.getStatus() <= 4 ? STATUS_DESCS[r.getStatus()] : "未知")
                .adminRemark(r.getAdminRemark())
                .handleTime(r.getHandleTime())
                .trackingNo(r.getTrackingNo())
                .createTime(r.getCreateTime())
                .updateTime(r.getUpdateTime())
                .build();
    }
}
