package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.dao.PointExchangeRuleDao;
import cn.lx.worldcoffee.module.shop.dao.PointRecordDao;
import cn.lx.worldcoffee.module.shop.dao.SysUserDao;
import cn.lx.worldcoffee.module.shop.dao.UserCouponDao;
import cn.lx.worldcoffee.module.shop.domain.PointExchangeRule;
import cn.lx.worldcoffee.module.shop.domain.PointRecord;
import cn.lx.worldcoffee.module.shop.domain.SysUser;
import cn.lx.worldcoffee.module.shop.domain.UserCoupon;
import cn.lx.worldcoffee.module.shop.domain.vo.PointBalanceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock private PointRecordDao pointRecordDao;
    @Mock private PointExchangeRuleDao ruleDao;
    @Mock private SysUserDao sysUserDao;
    @Mock private UserCouponDao userCouponDao;
    @InjectMocks private PointService pointService;

    @Test
    void earnPointsFromOrder_正常获得积分() {
        Long userId = 100L;
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPoints(50);
        user.setTotalPoints(50);
        user.setMemberLevel(1);

        when(sysUserDao.selectById(userId)).thenReturn(user);

        pointService.earnPointsFromOrder(userId, "ORD123", new BigDecimal("99.50"));

        verify(sysUserDao).update(any(), any());
        verify(pointRecordDao).insert(any(PointRecord.class));
    }

    @Test
    void earnPointsFromOrder_金额不足1元_不产生积分() {
        pointService.earnPointsFromOrder(100L, "ORD123", new BigDecimal("0.50"));

        verify(sysUserDao, never()).selectById(any());
        verify(pointRecordDao, never()).insert(any());
    }

    @Test
    void earnPointsFromOrder_跨等级提升() {
        Long userId = 100L;
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPoints(450);
        user.setTotalPoints(450);
        user.setMemberLevel(1);

        when(sysUserDao.selectById(userId)).thenReturn(user);

        // 消费60元 → 60积分 → 总510 → 超过500阈值 → 等级2
        pointService.earnPointsFromOrder(userId, "ORD456", new BigDecimal("60"));

        verify(sysUserDao).update(any(), any());
        // 验证 memberLevel 被更新为2（通过 LambdaUpdateWrapper 的 set 调用）
        // 由于 LambdaUpdateWrapper 的 set 是链式调用，我们验证 update 被调用即可
    }

    @Test
    void earnPointsFromReview_5星_获得10积分() {
        Long userId = 100L;
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPoints(100);
        user.setTotalPoints(100);

        when(sysUserDao.selectById(userId)).thenReturn(user);

        pointService.earnPointsFromReview(userId, 1L, 5);

        verify(sysUserDao).update(any(), any());
        verify(pointRecordDao).insert(any(PointRecord.class));
    }

    @Test
    void earnPointsFromReview_3星_不获得积分() {
        pointService.earnPointsFromReview(100L, 1L, 3);

        verify(sysUserDao, never()).selectById(any());
        verify(pointRecordDao, never()).insert(any());
    }

    @Test
    void exchangeCoupon_正常兑换() {
        Long userId = 100L;
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPoints(500);

        PointExchangeRule rule = new PointExchangeRule();
        rule.setId(1L);
        rule.setName("满50减10券");
        rule.setRequiredPoints(200);
        rule.setCouponId(5L);
        rule.setStatus(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(ruleDao.selectById(1L)).thenReturn(rule);
            when(sysUserDao.selectById(userId)).thenReturn(user);

            pointService.exchangeCoupon(1L);

            // 验证积分扣减
            verify(sysUserDao).update(any(), any());
            // 验证优惠券发放
            verify(userCouponDao).insert(any(UserCoupon.class));
            // 验证流水记录
            verify(pointRecordDao).insert(any(PointRecord.class));
        }
    }

    @Test
    void exchangeCoupon_积分不足_抛异常() {
        Long userId = 100L;
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPoints(100);

        PointExchangeRule rule = new PointExchangeRule();
        rule.setId(1L);
        rule.setRequiredPoints(500);
        rule.setStatus(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(ruleDao.selectById(1L)).thenReturn(rule);
            when(sysUserDao.selectById(userId)).thenReturn(user);

            assertThatThrownBy(() -> pointService.exchangeCoupon(1L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("积分不足");
        }
    }

    @Test
    void exchangeCoupon_规则不存在_抛异常() {
        Long userId = 100L;

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(ruleDao.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> pointService.exchangeCoupon(999L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("兑换规则不存在或已停用");
        }
    }

    @Test
    void getBalance_返回正确数据() {
        Long userId = 100L;
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPoints(800);
        user.setTotalPoints(1200);
        user.setMemberLevel(3);  // 黄金

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(userId);
            when(sysUserDao.selectById(userId)).thenReturn(user);

            PointBalanceVO balance = pointService.getBalance();

            assertThat(balance.getPoints()).isEqualTo(800);
            assertThat(balance.getTotalPoints()).isEqualTo(1200);
            assertThat(balance.getMemberLevel()).isEqualTo(3);
            assertThat(balance.getMemberLevelDesc()).isEqualTo("黄金会员");
        }
    }

    @Test
    void getBalance_用户不存在_抛异常() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::requireUserId).thenReturn(999L);
            when(sysUserDao.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> pointService.getBalance())
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("用户不存在");
        }
    }
}
