package cn.lx.worldcoffee.module.shop.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.db.sql.Order;
import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.shop.dao.CoffeeOrderDao;
import cn.lx.worldcoffee.module.shop.dao.PaymentRecordDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeOrder;
import cn.lx.worldcoffee.module.shop.domain.PaymentRecord;
import cn.lx.worldcoffee.module.shop.domain.vo.PaymentResultVO;
import cn.lx.worldcoffee.module.shop.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MockPaymentServiceImpl implements PaymentService {

    private final CoffeeOrderDao orderDao;
    private final PaymentRecordDao paymentRecordDao;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public PaymentResultVO createPayment(Long userId, String orderNo) {
        //1.查订单
        CoffeeOrder order = orderDao.selectOne(new LambdaQueryWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getOrderNo, orderNo));
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权操作");
        if (order.getStatus() != 0) throw new ServiceException("订单状态不允许支付");

        // 2. 幂等：是否已经创建过支付记录
        PaymentRecord exist = paymentRecordDao.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderNo, orderNo)
                .eq(PaymentRecord::getStatus, 0));
        if (exist != null){
            return PaymentResultVO.builder()
                    .orderId(order.getId())   // 新增
                    .orderNo(orderNo)
                    .transactionId(exist.getTransactionId())
                    .amount(exist.getAmount())
                    .payUrl("/api/shop/pay/mock-pay?transactionId=" + exist.getTransactionId())
                    .status(0)
                    .build();
        }

        // 3. 创建支付记录
        String transactionId = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        PaymentRecord record = new PaymentRecord();
        record.setOrderNo(orderNo);
        record.setTransactionId(transactionId);
        record.setAmount(order.getTotalAmount());
        record.setStatus(0);
        record.setCreateTime(LocalDateTime.now());
        paymentRecordDao.insert(record);

        // 4. 发送超时检查延时消息（15 分钟）
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDER_TIMEOUT_QUEUE,
                RabbitConfig.ORDER_TIMEOUT_DELAY_ROUTING_KEY,
                orderNo
        );
        // 5. 返回 Mock 支付信息
        return PaymentResultVO.builder()
                .orderNo(orderNo)
                .transactionId(transactionId)
                .amount(order.getTotalAmount())
                .payUrl("/api/shop/pay/mock-pay?transactionId=" + transactionId)
                .status(0)
                .build();
    }

    @Override
    public void handleCallback(String orderNo, String transactionId) {
        // 1. 查支付记录
        PaymentRecord record = paymentRecordDao.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getTransactionId, transactionId));
        if (record == null) throw new ServiceException("支付记录不存在");
        if (!record.getOrderNo().equals(orderNo)) throw new ServiceException("订单号不一致");
        if (record.getStatus() == 1) return;  // 已支付，幂等返回
        if (record.getStatus() == 2) throw new ServiceException("支付已失败");

        // 2. 查订单
        CoffeeOrder order = orderDao.selectOne(new LambdaQueryWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getOrderNo, orderNo));
        if (order == null) throw new ServiceException("订单不存在");
        if (order.getStatus() != 0) throw new ServiceException("订单状态不允许支付");

        // 3. 更新支付记录
        record.setStatus(1);
        record.setPayTime(LocalDateTime.now());
        paymentRecordDao.updateById(record);

        // 4. 更新订单状态为已支付
        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        orderDao.updateById(order);
    }

    @Override
    public Integer queryStatus(String orderNo) {
        PaymentRecord record = paymentRecordDao.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderNo, orderNo));
        return record == null ? null : record.getStatus();
    }
}
