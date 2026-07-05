package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.shop.dao.CouponDao;
import cn.lx.worldcoffee.module.shop.dao.CouponProductDao;
import cn.lx.worldcoffee.module.shop.dao.UserCouponDao;
import cn.lx.worldcoffee.module.shop.domain.Coupon;
import cn.lx.worldcoffee.module.shop.domain.UserCoupon;
import cn.lx.worldcoffee.module.shop.domain.vo.CouponVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponDao couponDao;
    private final UserCouponDao userCouponDao;
    private final CouponProductDao couponProductDao;

    private Long getCurrentUserId() {
        try {
            //// 等价于（完整写法）
            //Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            //var 是 Java 10 以后的关键字，意思是"让编译器自动推断类型"。
            //var 不是"一种数据类型"——它只是个偷懒的写法，编译的时候 Java 会自动把 var 替换成真实的类型。
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return Long.valueOf(auth.getPrincipal().toString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 可领取的优惠券列表 */
    public List<CouponVO> listAvailableCoupons() {
        Long userId = getCurrentUserId();
        //SELECT * FROM coupon
        //WHERE status = 1                    -- 已上架的
        //  AND start_time <= NOW()           -- 已经开始的
        //  AND end_time >= NOW()             -- 还没过期的
        List<Coupon> coupons = couponDao.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .le(Coupon::getStartTime, LocalDateTime.now())
                .ge(Coupon::getEndTime, LocalDateTime.now())
        );

        return coupons.stream().map(c -> {
            //SELECT COUNT(*) FROM user_coupon
            //WHERE user_id = 5 AND coupon_id = 1
            //大于 0 = 领过了 → claimed = true → 前端显示"已领取"，不让再领。
            boolean claimed = false;
            if (userId != null) {
                claimed = userCouponDao.selectCount(new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getCouponId, c.getId())
                ) > 0;
            }
            return CouponVO.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .type(c.getType())
                    .value(c.getValue())
                    .minAmount(c.getMinAmount())
                    .stock(c.getStock())
                    .claimed(claimed)   // ← 前端根据这个显示"领取"或"已领取"
                    .endTime(c.getEndTime())
                    .build();
        }).collect(Collectors.toList());
    }

    /** 用户领取优惠券 */
    public void claimCoupon(Long couponId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        Coupon coupon = couponDao.selectById(couponId);
        if (coupon == null || coupon.getStatus() == 0) throw new ServiceException("优惠券不存在");

        // 检查是否已领取
        Long count = userCouponDao.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
        if (count > 0) throw new ServiceException("已领取过该优惠券");

        // 检查库存
        if (coupon.getStock() > 0) {
            Long claimedCount = userCouponDao.selectCount(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getCouponId, couponId));
            if (claimedCount >= coupon.getStock()) throw new ServiceException("优惠券已被领完");
        }
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setUsed(0);
        uc.setCreateTime(LocalDateTime.now());
        userCouponDao.insert(uc);
    }

    /** 我的优惠券 */
    public List<CouponVO> myCoupons() {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        List<UserCoupon> myList = userCouponDao.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getUsed, 0));

        return myList.stream().map(uc -> {
            Coupon c = couponDao.selectById(uc.getCouponId());
            if (c == null) return null;
            return CouponVO.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .type(c.getType())
                    .value(c.getValue())
                    .minAmount(c.getMinAmount())
                    .endTime(c.getEndTime())
                    .claimed(true)
                    .build();
        }).filter(v -> v != null).collect(Collectors.toList());
    }
}


/**
 * 三个业务流程
 *
 * ① 领券流程（用户 → 领券 → 拿到券）
 * 用户打开商城 → 看到优惠券列表
 *     │
 *     ├─ 满 100 减 20（还有 50 张）
 *     ├─ 8 折券（不限量）
 *     └─ 耶加雪菲秒杀价 80 元（限 10 张）
 *     │
 *     ▼
 * 用户点"领取"
 *     │
 *     ├─ 检查有没有登录 → 没登录让登录
 *     ├─ 检查有没有领过 → 领过了不让再领
 *     ├─ 检查库存够不够 → 抢完了提示"已被领完"
 *     │
 *     └─ 通过 → user_coupon 表加一条记录
 *              → 用户看到"已领取"
 * 对应代码方法： claimCoupon() 和 listAvailableCoupons()
 *
 *
 * ② 使用优惠券（下单时）
 * 用户下单
 *     │
 *     ├─ 选择商品（比如咖啡豆 120 元）
 *     ├─ 选择优惠券（满 100 减 20）
 *     │
 *     ▼
 * 后端计算：
 *     │
 *     ├─ 原价 120 元
 *     ├─ 优惠券 -20 元
 *     │
 *     └─ 实付 100 元
 *
 * 注意： 这个流程现在还没写，因为我没给你下单时用券的接口。要补的话在后面加。
 *
 *
 *
 *③ 秒杀流程（限时 + 限库存）
 * 后台配置秒杀券：
 *     │
 *     ├─ 名称：耶加雪菲秒杀价
 *     ├─ 类型：3（秒杀券）
 *     ├─ 面额：直接减到 80 元
 *     ├─ 库存：10 张
 *     ├─ 开始：今晚 8 点
 *     └─ 结束：今晚 10 点
 *     │
 *     ▼
 * 用户 8 点准时来领：
 *     │
 *     ├─ 8 点前 → 优惠券不显示（startTime 没到）
 *     ├─ 8 点 - 10 点 → 可领取
 *     │       ├─ 前 10 个人 → 领到 ✅
 *     │       └─ 第 11 个人 → "已被领完" ❌
 *     │
 *     └─ 10 点后 → 优惠券过期（endTime 过了）
 *
 *
 * 想要理解的关键点：
 *
 * 概念	                说明
 * coupon	            优惠券模板（什么券、多少钱、什么时候有效）
 * user_coupon	        用户"领到"的记录（一个人可以领多张券）
 * coupon_product	    这张券能买哪些商品（空 = 全场通用）
 */
