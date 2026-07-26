package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.dao.CoffeeProductDao;
import cn.lx.worldcoffee.module.shop.dao.CouponDao;
import cn.lx.worldcoffee.module.shop.dao.CouponProductDao;
import cn.lx.worldcoffee.module.shop.dao.UserCouponDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeProduct;
import cn.lx.worldcoffee.module.shop.domain.Coupon;
import cn.lx.worldcoffee.module.shop.domain.CouponProduct;
import cn.lx.worldcoffee.module.shop.domain.from.SeckillForm;
import cn.lx.worldcoffee.module.shop.domain.vo.CouponVO;
import cn.lx.worldcoffee.module.shop.domain.vo.SeckillOrderResultVO;
import cn.lx.worldcoffee.module.shop.service.SeckillService;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shop/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final CouponDao couponDao;
    private final CouponProductDao couponProductDao;
    private final CoffeeProductDao coffeeProductDao;
    private final UserCouponDao userCouponDao;
    private final RedissonClient redissonClient;
    private final SeckillService seckillService;

    /** 秒杀活动列表 */
    @GetMapping("/activities")
    public Result<List<CouponVO>> listActivities() {
        List<Coupon> coupons = couponDao.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getType, 3)
                .eq(Coupon::getStatus, 1)
                .le(Coupon::getStartTime, LocalDateTime.now())
                .ge(Coupon::getEndTime, LocalDateTime.now())
        );
        return Result.success(coupons.stream().map(c -> {
            List<CouponProduct> cpList = couponProductDao.selectList(
                    new LambdaQueryWrapper<CouponProduct>().eq(CouponProduct::getCouponId, c.getId()));
            CouponVO.CouponVOBuilder builder = CouponVO.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .type(c.getType())
                    .value(c.getValue())
                    .seckillPrice(c.getSeckillPrice())
                    .startTime(c.getStartTime())
                    .endTime(c.getEndTime())
                    .stock(c.getStock());
            if (!cpList.isEmpty()) {
                Long productId = cpList.get(0).getProductId();
                builder.productId(productId);
                // 补充商品信息
                CoffeeProduct product = coffeeProductDao.selectById(productId);
                if (product != null) {
                    builder.productName(product.getName());
                    // images 是 JSON 字符串，取第一张作为封面
                    if (product.getImages() != null && !product.getImages().isBlank()) {
                        try {
                            List<String> imgs = JSONUtil.toList(product.getImages(), String.class);
                            if (!imgs.isEmpty()) {
                                builder.productImage(imgs.get(0));
                            }
                        } catch (Exception ignored) {
                            builder.productImage(product.getImages());
                        }
                    }
                }
            }
            return builder.build();
        }).collect(Collectors.toList()));
    }

    /** 秒杀抢购（领券 + 下单一步完成） */
    @PostMapping("/buy")
    public Result<SeckillOrderResultVO> buy(@RequestBody SeckillForm form) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // 1. 校验秒杀 token
        //如果 validateSeckillToken 返回 false，就进入 if 分支。
        if (!seckillService.validateSeckillToken(form.getSeckillToken())) {
            return Result.fail("秒杀令牌无效或已过期");
        }

        String lockKey = "seckill:lock:" + userId + ":" + form.getCouponId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            //// leaseTime = -1 表示启用看门狗，自动续期
            boolean acquired = lock.tryLock(0, -1, TimeUnit.SECONDS);
            if (!acquired) {
                return Result.fail("请勿重复提交");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("系统繁忙，请重试");
        }

        try {
            String orderNo = seckillService.seckillBuy(userId, form);
            // 消耗 token
            seckillService.consumeSeckillToken(form.getSeckillToken());
            return Result.success(SeckillOrderResultVO.builder()
                            .orderNo(orderNo)
                            .status("PROCESSING")
                            .build());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    //用户看到秒杀商品 点击以后获得验证码
    @GetMapping("/captcha")
    public Result<String> getCaptcha() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");
        String captcha = seckillService.generateCaptcha(userId);
        return Result.success(captcha);
    }

    //用户输入验证码以后 请求秒杀token
    @PostMapping("/token")
    public Result<String> getToken(@RequestParam String captcha) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");
        String token = seckillService.generateSeckillToken(userId, captcha);
        return Result.success(token);
    }
    //先查userCuopon表判断用户有没有参与过 再插入userConpou + 创建订单
    //但是现在有一个问题 “查重->插入之间并没有并发的保护” 俩个人同时点就能通过检查 产生重复的订单
//    @PostMapping("/buy")
//    public Result<OrderVO> buy(@RequestBody SeckillForm form) {
//        Long userId = SecurityUtils.getCurrentUserId();
//        if (userId == null) throw new ServiceException("请先登录");
//
//        // 分布式锁 key：一个用户 + 一个活动 一把锁
//        String lockKey = "seckill:lock:" + userId + ":" + form.getCouponId();
//        RLock lock = redissonClient.getLock(lockKey);
//
//        try {
//            // waitTime=0：抢不到直接返回，不排队等待
//            //在秒杀场景下，这意味着同一个用户狂点按钮时，第一个请求抢到锁进去处理，后面再点的请求因为拿不到锁直接提示"请勿重复提交"。
//            // leaseTime=5：最多持锁5秒，异常时自动释放防止死锁
//            //Redisson 的 tryLock 方法签名是：
//            //boolean tryLock(long waitTime, long leaseTime, TimeUnit unit)
//            //等锁的最长时间。0 表示不等，拿不到锁立刻返回 false
//            //拿到锁后，锁自动释放的最长时间，这里是 5 秒
//            boolean acquired = lock.tryLock(0, 5, TimeUnit.SECONDS);
//            if (!acquired){
//                return Result.fail("请勿重复提交");
//            }
//        }catch (InterruptedException e){
//            Thread.currentThread().interrupt();
//            return Result.fail("系统繁忙，请重试");
//        }
//
//        try {
//            // 1. 校验秒杀券
//            Coupon coupon = couponDao.selectById(form.getCouponId());
//            if (coupon == null || coupon.getType() != 3) throw new ServiceException("活动不存在");
//            if (coupon.getStatus() == 0) throw new ServiceException("活动已下架");
//            if (LocalDateTime.now().isBefore(coupon.getStartTime())) throw new ServiceException("活动未开始");
//            if (LocalDateTime.now().isAfter(coupon.getEndTime())) throw new ServiceException("活动已结束");
//
//            // 2. 校验是否已抢购过
//            Long count = userCouponDao.selectCount(new LambdaQueryWrapper<UserCoupon>()
//                    .eq(UserCoupon::getUserId, userId)
//                    .eq(UserCoupon::getCouponId, form.getCouponId()));
//            if (count > 0) throw new ServiceException("已参加过该活动");
//
//            // 3. 记录领券
//            UserCoupon uc = new UserCoupon();
//            uc.setUserId(userId);
//            uc.setCouponId(form.getCouponId());
//            uc.setUsed(1); // 领券即使用（秒杀专用）
//            uc.setCreateTime(LocalDateTime.now());
//
//            try {
//                userCouponDao.insert(uc);
//            } catch (DuplicateKeyException e) {
//                //org.springframework.dao.DuplicateKeyException，
//                // Spring 包装后的异常，MyBatis-Plus 插入违反唯一索引时会抛这个
//                // DB 唯一索引兜底：极端并发或 Redis 异常时拦截重复订单
//                throw new ServiceException("已参加过该活动");
//            }
//
//            // 4. 创建订单（调用现有下单逻辑，传入秒杀价和优惠券ID）
//            OrderVO order = shopService.createOrder(form.toCreateOrderForm(), coupon.getSeckillPrice());
//            return Result.success(order);
//
//        }finally {
//            //锁为什么要加 isHeldByCurrentThread() 判断？
//            //防止 tryLock 失败或锁已经过期时，误释放别人的锁。
//            // 只释放自己持有的锁
//            if (lock.isHeldByCurrentThread()) {
//                lock.unlock();
//            }
//        }
//    }

}
