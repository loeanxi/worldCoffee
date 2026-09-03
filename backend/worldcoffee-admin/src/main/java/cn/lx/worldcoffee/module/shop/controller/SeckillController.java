package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.result.Result;
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

}
