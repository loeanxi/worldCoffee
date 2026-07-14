package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.module.shop.domain.vo.PaymentResultVO;

public interface PaymentService {

    /**
     * 发起支付
     */
    PaymentResultVO createPayment(Long userId, String orderNo);

    /**
     * 处理支付回调
     */
    void handleCallback(String orderNo, String transactionId);

    /**
     * 查询支付状态
     */
    Integer queryStatus(String orderNo);
}
