package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.module.shop.domain.from.CreateOrderFrom;
import cn.lx.worldcoffee.module.shop.domain.message.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final StringRedisTemplate redisTemplate;
    private final ShopService shopService;

    @RabbitListener(queues = RabbitConfig.SECKILL_ORDER_QUEUE)
    public void handle(SeckillOrderMessage msg){
        String key = "seckill:order:" + msg.getOrderNo();

        // 1. SETNX 预检幂等
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", 5, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(isNew)){
            log.warn("重复消息，已处理过，orderNo={}",msg.getOrderNo());
            return;
        }

        try {
            // 2. 设置 SecurityContext，让 ShopService 能拿到 userId
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(String.valueOf(msg.getUserId()), null, null);
            SecurityContextHolder.getContext().setAuthentication(auth);

            // 3. 创建订单
            CreateOrderFrom form = new CreateOrderFrom();
            form.setCouponId(msg.getCouponId());
            form.setAddress(msg.getAddress());
            form.setRemark(msg.getRemark());

            shopService.createOrder(form, msg.getSeckillPrice());

        }catch (Exception e){
            log.error("秒杀订单创建失败，orderNo={}", msg.getOrderNo(), e);
            // 失败时删除 SETNX，让重试能进来
            redisTemplate.delete(key);
            throw e;  // 抛异常触发 MQ 重试
        }finally {
            SecurityContextHolder.clearContext();
        }
    }
}
