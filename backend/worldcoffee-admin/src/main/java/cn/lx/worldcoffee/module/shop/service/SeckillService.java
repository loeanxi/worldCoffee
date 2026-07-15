package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.config.RabbitConfig;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.shop.dao.CouponDao;
import cn.lx.worldcoffee.module.shop.dao.SeckillEventDao;
import cn.lx.worldcoffee.module.shop.dao.UserCouponDao;
import cn.lx.worldcoffee.module.shop.domain.Coupon;
import cn.lx.worldcoffee.module.shop.domain.SeckillEvent;
import cn.lx.worldcoffee.module.shop.domain.UserCoupon;
import cn.lx.worldcoffee.module.shop.domain.from.SeckillForm;
import cn.lx.worldcoffee.module.shop.domain.message.SeckillOrderMessage;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.util.OrderNoGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SeckillService {
    private final CouponDao couponDao;
    private final UserCouponDao userCouponDao;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final SeckillEventDao seckillEventDao;
    private final OrderNoGenerator orderNoGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 引入rabbitmq后不在由这个方法去创建订单
     * 采用发消息的方式去异步创建一个订单
     * <p>
     * 该方法作用：
     * 校验资格
     * 插 UserCoupon
     * 生成订单号
     * 发 MQ
     * 返回订单号
     */
    @Transactional
    public String seckillBuy(Long userId, SeckillForm form) {
        // 1. 校验秒杀券
        Coupon coupon = couponDao.selectById(form.getCouponId());
        if (coupon == null || coupon.getType() != 3) throw new ServiceException("活动不存在");
        if (coupon.getStatus() == 0) throw new ServiceException("活动已下架");
        if (LocalDateTime.now().isBefore(coupon.getStartTime())) throw new ServiceException("活动未开始");
        if (LocalDateTime.now().isAfter(coupon.getEndTime())) throw new ServiceException("活动已结束");

        // 2. Redis 预扣秒杀库存
        //注意：这里预扣的是秒杀券库存，不是商品库存。商品库存还是在 createOrder 里扣
        String stockKey = "seckill:stock:" + form.getCouponId();
        Long stock = redisTemplate.opsForValue().decrement(stockKey);
        if (stock == null || stock < 0) {
            if (stock != null) {
                redisTemplate.opsForValue().increment(stockKey);
            }
            throw new ServiceException("秒杀库存不足");
        }

        try {
            // 3. 校验是否已抢购过
            Long count = userCouponDao.selectCount(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getUserId, userId)
                    .eq(UserCoupon::getCouponId, form.getCouponId()));
            if (count > 0) throw new ServiceException("已参加过该活动");

            // 5. 生成订单号
            String orderNo = orderNoGenerator.nextOrderNo();

            // 6. 构造 MQ 消息
            SeckillOrderMessage msg = new SeckillOrderMessage();
            msg.setUserId(userId);
            msg.setCouponId(form.getCouponId());
            msg.setProductId(form.getProductId());
            msg.setOrderNo(orderNo);
            msg.setAddress(form.getAddress());
            msg.setRemark(form.getRemark());
            msg.setSeckillPrice(coupon.getSeckillPrice());

            // 4. 记录领券 + 事务（本地事务）
            UserCoupon uc = new UserCoupon();
            uc.setUserId(userId);
            uc.setCouponId(form.getCouponId());
            uc.setUsed(1);
            uc.setCreateTime(LocalDateTime.now());
            try {
                userCouponDao.insert(uc);
            } catch (DuplicateKeyException e) {
                throw new ServiceException("已参加过该活动");
            }

            // 7. 写入 seckill_event
            SeckillEvent event = new SeckillEvent();
            event.setOrderNo(orderNo);
            event.setUserId(userId);
            event.setCouponId(form.getCouponId());
            event.setStatus(0);  // 待发送

            try {
                event.setPayload(objectMapper.writeValueAsString(msg));
            } catch (JsonProcessingException e) {
                throw new ServiceException("事件序列化失败");
            }
            event.setCreateTime(LocalDateTime.now());
            seckillEventDao.insert(event);

            // 8. 发送 MQ（事务外，但方法内）
            rabbitTemplate.convertAndSend(RabbitConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitConfig.SECKILL_ORDER_ROUTING_KEY, msg);

            // 9. 更新事件状态为已发送
            event.setStatus(1);
            event.setUpdateTime(LocalDateTime.now());
            seckillEventDao.updateById(event);

            return orderNo;
        } catch (Exception e) {
            // 预扣的秒杀库存需要回滚
            redisTemplate.opsForValue().increment(stockKey);
            throw e;
        }

    }


//    private static final Random RANDOM = new Random();

//    private String generateOrderNo(Long userId) {
//        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
//                + String.format("%04d", userId % 10000)
//                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
//    }

    public String generateCaptcha(Long userId) {
        // 随机 4 位验证码
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 1000));
        String key = "seckill:captcha:" + userId;
        redisTemplate.opsForValue().set(key, code, 60, TimeUnit.SECONDS);
        return code;
    }

    public boolean validateCaptcha(Long userId, String captcha) {
        String key = "seckill:captcha:" + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) return false;
        return cached.equalsIgnoreCase(captcha);
    }


    public String generateSeckillToken(Long userId, String captcha) {
        // 校验验证码
        if (!validateCaptcha(userId, captcha)) {
            throw new ServiceException("验证码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        String key = "seckill:token:" + token;
        redisTemplate.opsForValue().set(key, userId.toString(), 60, TimeUnit.SECONDS);
        return token;
    }


    public boolean validateSeckillToken(String token) {
        String key = "seckill:token:" + token;
        String value = redisTemplate.opsForValue().get(key);
        //这里返回 value != null 是因为我们只想知道 token 在 Redis 里是否还存在
        //key 是 token 本身：seckill:token:{uuid}
        //value 是 userId：记录是谁的 token
        //当用户带 token 来秒杀时，我们不需要知道 value 是多少，只要确认这个 token 还在有效期内就行。
        return value != null;
    }

    public void consumeSeckillToken(String token) {
        String key = "seckill:token:" + token;
        redisTemplate.delete(key);
    }
}
    //避免每次new一个实例random   这样只创建一个 Random 实例
/**new random是怎么选中子的
 * 种子是什么
 * Random 生成随机数不是真正随机的，而是伪随机。它内部有一个公式：
 *
 * 下一个随机数 = f(上一个状态)
 * 这个"初始状态"就是种子（seed）。
 *
 * 只要种子一样，生成的随机数序列就一样。
 *
 * when you new random()
 * java内部会调用this.seed = system.nanoTime() + something
 * System.nanoTime() 是当前时间的纳秒值。 Java 用它来当随机数的初始种子。
 * 为什么时间当种子可能重复
 * 纳秒听起来很细，但是：
 * // 线程 A
 * new Random().nextInt(1000);
 * // 线程 B
 * new Random().nextInt(1000);
 * 如果 A 和 B 在同一纳秒执行了 new Random()，那它们的种子就可能一样，生成的随机数也可能一样。
 * 在秒杀这种高并发场景，同一时刻很多请求进来，重复概率就高了。
 * 假设两个线程同时执行：
 *
 * Thread 1: Random r1 = new Random();  // 种子 = 1234567890000
 * Thread 2: Random r2 = new Random();  // 种子 = 1234567890000  （同一纳秒）
 *
 * r1.nextInt(1000)  // 假设 = 777
 * r2.nextInt(1000)  // 也是 777
 * 如果两个订单号的随机部分都是 777，加上同一秒同一用户，订单号就撞了。
 */
/**
 * new Random() 的问题:
 * 每次调用：
 * new Random().nextInt(1000)
 * 都会做这些事：
 *
 * 1分配内存，创建 Random 对象
 * 2用当前时间 System.nanoTime() 算种子
 * 3生成随机数
 * 4对象很快被 GC 回收
 * 在高并发场景下，比如秒杀，一秒钟可能有成千上万请求，很多请求可能在同一纳秒内创建 Random，种子相同，导致随机数重复。
 *
 * 为什么一个实例更好
 * 只初始化一次种子
 * 后续直接复用内部状态
 * 减少对象创建和 GC 压力
 */

//生成订单号： 时间 + 用户id + 随机数
//    private String generateOderNo(Long userId) {
//        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
//                + String.format("%04d", userId % 10000)
//                + String.format("%03d",new Random().nextInt(1000));
//    }
    //假设：
    //
    //当前时间：2026-07-05 09:45:12
    //用户 ID：123456
    //随机数：7
    //"20260705094512" + "3456" + "007"
    //= "202607050945123456007"

//    %03d 把它格式化成 3 位，不足补 0
//    比如随机数是 7，格式化成 "007"；随机数是 99，格式化成 "099"。

/**本质：
 * new Random(seed) 就是指定一个起始位置。
 * Random r1 = new Random(123);
 * r1.nextInt(1000);  // 782
 *
 * Random r2 = new Random(123);
 * r2.nextInt(1000);  // 还是 782
 * 因为 r1 和 r2 都指定了同一个起始位置 123，它们看到的第一个数当然一样。
 *
 * Random r = new Random(123);
 * r.nextInt(1000);  // 782
 * r.nextInt(1000);  // 450
 * r.nextInt(1000);  // 176
 * 同一个 r 第一次看位置 0，第二次看位置 1，第三次看位置 2，所以不一样。
 *
 * 秒杀场景的问题
 * new Random().nextInt(1000)   // 线程 A 执行
 * new Random().nextInt(1000)   // 线程 B 执行
 * 这两个 new Random() 没有指定种子，Java 会自动用当前时间当种子：
 * new Random(System.nanoTime())
 * 如果线程 A 和线程 B 在同一纳秒执行，它们的种子就一样，起始位置一样，第一个随机数就一样。
 *
 * new Random().nextInt()	每次都在随机序列开头附近重新站一个位置	位置可能重复，数字可能重复
 * 同一个 Random 实例多次 nextInt()	同一个人一直往前走	位置不会重复，数字不会重复
 *
 * 通过一个一个静态常量实例解决了重复问题？ 但是线程安全吗？
 */


/**
 * java.util.Random 不是线程安全的，
 * 因为它内部有一个共享状态 seed，多个线程同时修改这个 seed 会互相覆盖。
 *
 * public class Random {
 *     private long seed;  // 内部状态
 *
 *     protected int next(int bits) {
 *         // 读取 seed
 *         long oldSeed = this.seed;
 *         // 计算下一个 seed
 *         long nextSeed = (oldSeed * 25214903917L + 11) & ((1L << 48) - 1);
 *         // 写回 seed
 *         this.seed = nextSeed;
 *         return (int) (nextSeed >>> (48 - bits));
 *     }
 * }
 * nextInt() 最终会调用类似 next(32) 的逻辑，核心就是读 seed → 算新 seed → 写回 seed。
 *
 * 线程不安全在哪
 * 假设线程 A 和线程 B 同时执行：
 * 时间线：
 *   A 读取 seed = 100
 *   B 读取 seed = 100
 *   A 计算 nextSeed = 200，写回 seed = 200
 *   B 还用旧的 seed = 100，计算 nextSeed = 200，写回 seed = 200
 *结果：
 * A 和 B 算出来的随机数一样
 * 中间丢失了一个状态
 *
 * 实际影响
 * 随机数重复：两个线程拿到一样的数
 * 状态错乱：极端情况下可能生成可预测序列
 * 不是原子操作：读-改-写没有同步
 *
 * 为什么 ThreadLocalRandom 就行
 * ThreadLocalRandom.current().nextInt(1000);
 *
 * ThreadLocalRandom 给每个线程单独一个 Random 实例，线程之间互不干扰：
 * // 线程 A
 * Random r1 = threadLocal.get();  // 线程 A 自己的实例
 * // 线程 B
 * Random r2 = threadLocal.get();  // 线程 B 自己的实例
 *
 * 所以秒杀里推荐：
 * ThreadLocalRandom.current().nextInt(1000)
 * 而不是：
 * new Random().nextInt(1000)  // 可能重复
 * private static final Random RANDOM = new Random();
 * RANDOM.nextInt(1000);  // 线程不安全
 *
 *
 * 对比
 * 写法	                                   可能重复原因	              是否推荐
 * new Random().nextInt(1000)	       时间种子相同，起始状态相同	       不推荐
 * static Random 单实例	                  线程竞争 + 自然重复	           不推荐（多线程）
 * ThreadLocalRandom.current().nextInt(1000)	仅自然重复	           推荐
 * static final Random 比 new Random() 好，因为不会出现时间种子相同的系统性重复。但它仍然有线程安全问题，高并发下仍可能重复。
 *
 * 秒杀场景下，用 ThreadLocalRandom 最稳妥：
 * ThreadLocalRandom.current().nextInt(1000)
 * 它既没有 new Random() 的种子重复问题，也没有 static Random 的线程安全问题，只会有自然的随机重复（概率极低）。
 *
 * 为什么比 static Random 快
 * 方式	             多线程行为	                     性能
 * static Random	所有线程抢同一个 seed，需要同步/竞争	慢，线程越多越慢
 * ThreadLocalRandom	每个线程用自己的 seed，互不干扰	快，无竞争
 *
 * 内存开销
 * 每个线程一个 ThreadLocalRandom 实例，内存占用极小，几乎可以忽略。
 * 线程结束后，ThreadLocal 里的对象会被 GC 回收，不会永久占用内存。
 *
 *
 * 调用过程
 *
 * ThreadLocalRandom.current().nextInt(1000);
 * 分两步：
 *
 * current()：去当前线程的 ThreadLocalMap 里找 ThreadLocalRandom 实例
 *
 * 第一次：找不到，调用 initialValue() 创建一个
 * 第二次及以后：直接拿到上次那个
 * nextInt(1000)：用这个实例生成随机数
 *
 * 和 static Random 的对比
 * static Random
 *
 * private static final Random RANDOM = new Random();
 *
 * // 线程 A ----> 同一个 RANDOM
 * // 线程 B ----> 同一个 RANDOM
 * // 线程 C ----> 同一个 RANDOM
 * 所有线程共享一个 Random 实例，共享一个 seed。
 *
 * 线程 A 读 seed 线程 B 读 seed 线程 C 读 seed ↓ 同时改同一个 seed ↓ 状态互相覆盖
 * ThreadLocalRandom
 *
 * ThreadLocalRandom.current().nextInt(1000);
 *
 * // 线程 A ----> ThreadLocalRandom 实例 A
 * // 线程 B ----> ThreadLocalRandom 实例 B
 * // 线程 C ----> ThreadLocalRandom 实例 C
 * 每个线程有自己的 ThreadLocalRandom 实例，有自己的 seed。
 *
 * 线程 A 的 seed: 100 → 200 → 300 线程 B 的 seed: 50 → 150 → 250 线程 C 的 seed: 80 → 180 → 280
 * 互不干扰。
 */

