package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.shop.dao.CouponDao;
import cn.lx.worldcoffee.module.shop.dao.UserCouponDao;
import cn.lx.worldcoffee.module.shop.domain.Coupon;
import cn.lx.worldcoffee.module.shop.domain.UserCoupon;
import cn.lx.worldcoffee.module.shop.domain.from.SeckillForm;
import cn.lx.worldcoffee.module.shop.domain.vo.OrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SeckillService {
    private final CouponDao couponDao;
    private final UserCouponDao userCouponDao;
    private final ShopService shopService;

    @Transactional
    public OrderVO seckillBuy(Long userId, SeckillForm form) {
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
        uc.setUsed(1);
        uc.setCreateTime(LocalDateTime.now());

        try {
            userCouponDao.insert(uc);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("已参加过该活动");
        }

        // 4. 创建订单
        return shopService.createOrder(form.toCreateOrderForm(), coupon.getSeckillPrice());
    }
}
