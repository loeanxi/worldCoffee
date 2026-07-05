package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.module.shop.dao.CouponDao;
import cn.lx.worldcoffee.module.shop.domain.Coupon;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SeckillStockCacheService {

    private final CouponDao couponDao;
    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    public void init() {
        // 加载所有进行中的秒杀活动库存
        List<Coupon> coupons = couponDao.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getType, 3)
                .eq(Coupon::getStatus, 1)
                .le(Coupon::getStartTime, LocalDateTime.now())
                .ge(Coupon::getEndTime, LocalDateTime.now()));

        for (Coupon coupon : coupons) {
            String key = "seckill:stock:" + coupon.getId();
            redisTemplate.opsForValue().set(key, String.valueOf(coupon.getStock()));
        }
    }
}
