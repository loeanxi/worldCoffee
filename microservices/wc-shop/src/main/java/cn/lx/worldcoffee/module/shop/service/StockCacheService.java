package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockCacheService {

    private final CoffeeProductDao productDao;
    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    public void init(){
        // 启动时把 MySQL 库存同步到 Redis
        for (CoffeeProduct p : productDao.selectList(null)){
            String key = "product:stock:" + p.getId();
            redisTemplate.opsForValue().set(key,String.valueOf(p.getStock()));
        }
        System.out.println("[StockCache]库存已同步至redis");
    }
}
