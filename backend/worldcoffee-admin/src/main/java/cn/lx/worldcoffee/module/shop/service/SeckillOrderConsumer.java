package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.module.shop.dao.CoffeeOrderDao;
import cn.lx.worldcoffee.module.shop.dao.SeckillEventDao;
import cn.lx.worldcoffee.module.shop.domain.SeckillEvent;
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.module.shop.domain.message.SeckillOrderMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final StringRedisTemplate redisTemplate;
    private final OrderService orderService;
    private final SeckillEventDao seckillEventDao;
    private final CoffeeOrderDao orderDao;

    @RabbitListener(queues = RabbitConfig.SECKILL_ORDER_QUEUE)
    public void handle(SeckillOrderMessage msg){
        String key = "seckill:order:" + msg.getOrderNo();
        //抛异常是怎么让mq重试的 ？

        // 1. SETNX 预检幂等
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", 5, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(isNew)){
            log.warn("重复消息，已处理过，orderNo={}",msg.getOrderNo());
            return;
        }

        try {
            // 2. 创建订单（userId 从消息体显式传入，无需伪造 HTTP 安全上下文）
            CreateOrderFrom form = new CreateOrderFrom();
            form.setCouponId(msg.getCouponId());
            form.setAddress(msg.getAddress());
            form.setRemark(msg.getRemark());

            orderService.createOrderForUser(form, msg.getUserId(), msg.getSeckillPrice());

            // 3. 更新事件状态为已完成
            updateEventStatus(msg.getOrderNo(), 2);

        }catch (Exception e){
            log.error("秒杀订单创建失败，orderNo={}", msg.getOrderNo(), e);
            // 失败时删除 SETNX，让重试能进来
            redisTemplate.delete(key);
            throw e;  // 抛异常触发 MQ 重试
        }
    }

    private void updateEventStatus(String orderNo, int status) {
        try {
            SeckillEvent event = seckillEventDao.selectOne(
                    new LambdaQueryWrapper<SeckillEvent>()
                            .eq(SeckillEvent::getOrderNo, orderNo));
            if (event != null) {
                event.setStatus(status);
                event.setUpdateTime(LocalDateTime.now());
                seckillEventDao.updateById(event);
            }
        } catch (Exception e) {
            log.error("更新 seckill_event 状态失败，orderNo={}", orderNo, e);
        }
    }
}
