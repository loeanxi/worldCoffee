package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.domain.from.PayCallbackForm;
import cn.lx.worldcoffee.module.shop.domain.vo.PaymentResultVO;
import cn.lx.worldcoffee.module.shop.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shop/pay")
public class PaymentController {

    private final PaymentService paymentService;


    /** Mock 支付回调（前端调用，模拟微信/支付宝通知后端） */
    @PostMapping("/callback")
    public Result<Void> callback(@RequestBody PayCallbackForm form) {
        paymentService.handleCallback(form.getOrderNo(), form.getTransactionId());
        return Result.success(null);
    }

    /** 查询支付状态 */
    @GetMapping("/status/{orderNo}")
    public Result<Integer> status(@PathVariable String orderNo) {
        return Result.success(paymentService.queryStatus(orderNo));
    }

}
