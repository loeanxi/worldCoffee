package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.module.shop.domain.message.SeckillOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SeckillOrderDeadLetterConsumer {

    @RabbitListener(queues = RabbitConfig.SECKILL_ORDER_DEAD_QUEUE)
    public void handle(SeckillOrderMessage msg){
        log.error("【死信队列】秒杀订单处理失败，需人工补偿，orderNo={}，userId={}",
                msg.getOrderNo(),msg.getUserId());
        // 这里可以接告警、写补偿表、发邮件等
    }
}
