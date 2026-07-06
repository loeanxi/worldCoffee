package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存管理服务 —— 所有跟 "product:stock:{productId}" 相关的操作都归这里。
 *
 * 职责：
 *   1. Redis Lua 原子扣减库存（高并发下不会超卖）
 *   2. Redis Lua 回滚库存（扣减失败时恢复）
 *   3. MySQL 库存同步（最终一致性：Redis 扣完，MySQL 也跟着扣）
 *   4. 管理员手动设库存（MySQL + Redis 双写）
 *
 * 为什么单独拆出来：
 *   原来库存逻辑散落在 ShopService 里，Lua 脚本初始化、扣减、回滚、MySQL 同步
 *   全混在 createOrder() 方法中，代码又长又难读。
 *   拆出来之后 OrderService 只需要调 inventoryService.deductStock()，
 *   不用关心 Lua 脚本怎么初始化、key 怎么拼。
 *
 * 依赖：
 *   - CoffeeProductDao：MySQL 库存读写
 *   - StringRedisTemplate：Redis 库存读写 + 执行 Lua 脚本
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryService {

    private final CoffeeProductDao productDao;
    private final StringRedisTemplate redisTemplate;

    // Lua 脚本对象，@PostConstruct 初始化
    private DefaultRedisScript<Long> stockLua;
    private DefaultRedisScript<Long> stockRollbackLua;

    /**
     * 初始化 Lua 脚本。
     * 用 @PostConstruct 而不是字段内联初始化，是因为这样更清晰，
     * 而且可以加日志排查加载失败的问题。
     *
     * stock.lua        —— 原子扣减：if stock >= quantity then stock -= quantity return 1 else return 0
     * stock_rollback.lua —— 原子回滚：stock += quantity
     */
    @PostConstruct
    public void initLua() {
        stockLua = new DefaultRedisScript<>();
        stockLua.setLocation(new ClassPathResource("stock.lua"));
        stockLua.setResultType(Long.class);

        stockRollbackLua = new DefaultRedisScript<>();
        stockRollbackLua.setLocation(new ClassPathResource("stock_rollback.lua"));
        stockRollbackLua.setResultType(Long.class);
    }

    // ==================== Redis 操作 ====================

    /**
     * Redis Lua 原子扣减库存。
     * 返回 1 = 扣减成功，返回 0 或 null = 库存不足。
     *
     * @param productId 商品 ID
     * @param quantity  要扣的数量
     */
    public Long deductStock(Long productId, int quantity) {
        String key = "product:stock:" + productId;
        return redisTemplate.execute(stockLua, List.of(key), String.valueOf(quantity));
    }

    /**
     * Redis Lua 回滚库存（扣减失败时恢复）。
     *
     * @param productId 商品 ID
     * @param quantity  要回滚的数量
     */
    public void rollbackStock(Long productId, int quantity) {
        String key = "product:stock:" + productId;
        redisTemplate.execute(stockRollbackLua, List.of(key), String.valueOf(quantity));
    }

    /**
     * 检查 Redis 库存是否充足（下单前的预检）。
     *
     * @param productId 商品 ID
     * @param required  需要的数量
     * @return true = 库存够用，false = 不够
     */
    public boolean checkStock(Long productId, int required) {
        String key = "product:stock:" + productId;
        String redisStock = redisTemplate.opsForValue().get(key);
        return redisStock != null && Integer.parseInt(redisStock) >= required;
    }

    // ==================== MySQL 操作（最终一致性同步） ====================

    /**
     * MySQL 库存扣减。
     * 在 Redis 扣完之后调用，保证 MySQL 和 Redis 最终一致。
     * SQL: UPDATE coffee_product SET stock = stock - ? WHERE id = ?
     */
    public void syncDeductToMySQL(Long productId, int quantity) {
        productDao.update(null, new LambdaUpdateWrapper<CoffeeProduct>()
                .setSql("stock = stock - " + quantity)
                .eq(CoffeeProduct::getId, productId));
    }

    /**
     * MySQL 库存回滚（取消订单时）。
     * SQL: UPDATE coffee_product SET stock = stock + ? WHERE id = ?
     */
    public void restoreStockMySQL(Long productId, int quantity) {
        productDao.update(null, new LambdaUpdateWrapper<CoffeeProduct>()
                .setSql("stock = stock + " + quantity)
                .eq(CoffeeProduct::getId, productId));
    }

    /**
     * Redis 库存回滚（取消订单时）。
     * 直接 increment，不需要 Lua（取消订单不是高并发场景）。
     */
    public void restoreStockRedis(Long productId, int quantity) {
        String key = "product:stock:" + productId;
        redisTemplate.opsForValue().increment(key, quantity);
    }

    // ==================== 管理员操作 ====================

    /**
     * 管理员手动设置库存：MySQL + Redis 双写，保持一致。
     *
     * 为什么要双写：
     *   MySQL 是"最终真相"，Redis 是"快速通道"（下单时先查 Redis）。
     *   管理员改了 MySQL 不改 Redis → 用户下单时 Redis 还是旧库存，会出问题。
     */
    public void updateStock(Long productId, Integer newStock) {
        CoffeeProduct product = productDao.selectById(productId);
        if (product == null) throw new ServiceException("商品不存在");

        product.setStock(newStock);
        productDao.updateById(product);

        // 同步到 Redis
        redisTemplate.opsForValue().set("product:stock:" + productId, String.valueOf(newStock));
    }
}
