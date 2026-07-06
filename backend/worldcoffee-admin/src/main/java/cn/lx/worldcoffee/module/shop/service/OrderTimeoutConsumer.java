package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.module.shop.dao.CoffeeOrderDao;
import cn.lx.worldcoffee.module.shop.dao.OrderItemDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeOrder;
import cn.lx.worldcoffee.module.shop.domain.OrderItem;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final CoffeeOrderDao orderDao;
    private final OrderService orderService;

    @RabbitListener(queues = RabbitConfig.ORDER_TIMEOUT_QUEUE)
    public void handle (String orderNo){
        log.info("订单超时检查，orderNo={}",orderNo);

        CoffeeOrder order = orderDao.selectOne(new LambdaQueryWrapper<CoffeeOrder>()
                .eq(CoffeeOrder::getOrderNo, orderNo));
        if (order == null){
            log.warn("订单不存在，orderNo={}", orderNo);
            return;
        }

        // 只有待支付才取消
        if (order.getStatus() != 0) {
            log.info("订单已处理，不需要取消，orderNo={}，status={}", orderNo, order.getStatus());
            return;
        }

        // 3. 调用 OrderService 统一取消
        orderService.cancelOrderBySystem(order.getId());


        log.info("订单超时已取消，orderNo={}", orderNo);

    }
}
