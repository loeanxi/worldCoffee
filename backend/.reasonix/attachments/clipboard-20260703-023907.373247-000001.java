package cn.lx.worldcoffee.module.shop.controller;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.shop.dao.CouponDao;
import cn.lx.worldcoffee.module.shop.dao.CouponProductDao;
import cn.lx.worldcoffee.module.shop.dao.UserCouponDao;
import cn.lx.worldcoffee.module.shop.domain.Coupon;
import cn.lx.worldcoffee.module.shop.domain.CouponProduct;
import cn.lx.worldcoffee.module.shop.domain.UserCoupon;
import cn.lx.worldcoffee.module.shop.domain.from.SeckillForm;
import cn.lx.worldcoffee.module.shop.domain.vo.CouponVO;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import cn.lx.worldcoffee.module.shop.service.ShopService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shop/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final CouponDao couponDao;
    private final CouponProductDao couponProductDao;
    private final UserCouponDao userCouponDao;
    private final ShopService shopService;

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
                    .endTime(c.getEndTime());
            if (!cpList.isEmpty()) {
                builder.productId(cpList.get(0).getProductId());
            }
            return builder.build();
        }).collect(Collectors.toList()));
    }

    /** 秒杀抢购（领券 + 下单一步完成） */
    @PostMapping("/buy")
    public Result<OrderVO> buy(@RequestBody SeckillForm form) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // 1. 校验秒杀券
        Coupon coupon = couponDao.selectById(form.getCouponId());
        if (coupon == null || coupon.getType() != 3) throw new ServiceException("活动不存在");
        if (coupon.getStatus() == 0) throw new ServiceException("活动已下架");
        if (LocalDateTime.now().isBefore(coupon.getStartTime())) throw new ServiceException("活动未开始");
        if (LocalDateTime.now().isAfter(coupon.getEndTime())) throw new ServiceException("活动已结束");

        // 2. 校验是否已抢购过
        Long count = userCouponDao.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, form.getCouponId()));
        if (count > 0) throw new ServiceException("已参加过该活动");

        // 3. 记录领券
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(form.getCouponId());
        uc.setUsed(1); // 领券即使用（秒杀专用）
        uc.setCreateTime(LocalDateTime.now());
        userCouponDao.insert(uc);

        // 4. 创建订单（调用现有下单逻辑，传入秒杀价和优惠券ID）
        OrderVO order = shopService.createOrder(form.toCreateOrderForm(), coupon.getSeckillPrice());
        return Result.success(order);
    }

    private Long getCurrentUserId() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return Long.valueOf(auth.getPrincipal().toString());
            }
        } catch (Exception ignored) {}
        return null;
    }
}
