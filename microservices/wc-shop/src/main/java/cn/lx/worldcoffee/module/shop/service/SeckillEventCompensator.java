package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.module.shop.dao.CoffeeOrderDao;
import cn.lx.worldcoffee.module.shop.dao.SeckillEventDao;
import cn.lx.worldcoffee.module.shop.dao.UserCouponDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeOrder;
import cn.lx.worldcoffee.module.shop.domain.SeckillEvent;
import cn.lx.worldcoffee.module.shop.domain.UserCoupon;
import cn.lx.worldcoffee.module.shop.domain.message.SeckillOrderMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillEventCompensator {

    private final SeckillEventDao seckillEventDao;
    private final CoffeeOrderDao orderDao;
    private final UserCouponDao userCouponDao;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每分钟执行一次补偿检查
     */
    @Scheduled(cron = "0 * * * * ?")
    public void compensate() {
        log.info("开始执行秒杀事件补偿检查");

        // 1. 处理长时间未发送的事件（status=0 超过 1 分钟）
        handleUnsentEvents();

        // 2. 处理长时间未完成的事件（status=1 超过 15 分钟）
        handleUncompletedEvents();
    }

    private void handleUnsentEvents() {
        List<SeckillEvent> events = seckillEventDao.selectList(
                new LambdaQueryWrapper<SeckillEvent>()
                        .eq(SeckillEvent::getStatus, 0)
                        .le(SeckillEvent::getCreateTime, LocalDateTime.now().minusMinutes(1)));

        for (SeckillEvent event : events) {
            try {
                log.info("重新发送秒杀 MQ，orderNo={}", event.getOrderNo());
                SeckillOrderMessage msg = objectMapper.readValue(event.getPayload(), SeckillOrderMessage.class);
                rabbitTemplate.convertAndSend(
                        RabbitConfig.SECKILL_ORDER_EXCHANGE,
                        RabbitConfig.SECKILL_ORDER_ROUTING_KEY,
                        msg);

                // 更新为已发送
                event.setStatus(1);
                event.setUpdateTime(LocalDateTime.now());
                seckillEventDao.updateById(event);
            } catch (Exception e) {
                log.error("重发秒杀消息失败，orderNo={}", event.getOrderNo(), e);
            }
        }
    }

    private void handleUncompletedEvents() {
        List<SeckillEvent> events = seckillEventDao.selectList(
                new LambdaQueryWrapper<SeckillEvent>()
                        .eq(SeckillEvent::getStatus, 1)
                        .le(SeckillEvent::getCreateTime, LocalDateTime.now().minusMinutes(15)));

        for (SeckillEvent event : events) {
            try {
                // 查订单是否存在
                CoffeeOrder order = orderDao.selectOne(
                        new LambdaQueryWrapper<CoffeeOrder>()
                                .eq(CoffeeOrder::getOrderNo, event.getOrderNo()));

                if (order != null) {
                    // 订单存在，说明创建成功了，更新状态为已完成
                    log.info("订单已存在，更新事件为完成，orderNo={}", event.getOrderNo());
                    event.setStatus(2);
                    event.setUpdateTime(LocalDateTime.now());
                    seckillEventDao.updateById(event);
                    continue;
                }

                // 订单不存在，触发补偿
                log.warn("订单不存在，开始补偿，orderNo={}", event.getOrderNo());
                compensateEvent(event);

            } catch (Exception e) {
                log.error("补偿检查失败，orderNo={}", event.getOrderNo(), e);
            }
        }
    }

    private void compensateEvent(SeckillEvent event) {
        try {
            // 1. 恢复 Redis 秒杀库存
            String stockKey = "seckill:stock:" + event.getCouponId();
            redisTemplate.opsForValue().increment(stockKey);

            // 2. 删除 user_coupon
            userCouponDao.delete(
                    new LambdaQueryWrapper<UserCoupon>()
                            .eq(UserCoupon::getUserId, event.getUserId())
                            .eq(UserCoupon::getCouponId, event.getCouponId()));

            // 3. 更新事件状态为已失败
            event.setStatus(3);
            event.setUpdateTime(LocalDateTime.now());
            seckillEventDao.updateById(event);

            log.info("补偿完成，orderNo={}", event.getOrderNo());
        } catch (Exception e) {
            log.error("补偿执行失败，orderNo={}", event.getOrderNo(), e);
        }
    }
}