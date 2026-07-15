package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.dao.CouponDao;
import cn.lx.worldcoffee.module.shop.dao.PointExchangeRuleDao;
import cn.lx.worldcoffee.module.shop.dao.PointRecordDao;
import cn.lx.worldcoffee.module.shop.dao.SysUserDao;
import cn.lx.worldcoffee.module.shop.domain.PointExchangeRule;
import cn.lx.worldcoffee.module.shop.domain.PointRecord;
import cn.lx.worldcoffee.module.shop.domain.SysUser;
import cn.lx.worldcoffee.module.shop.domain.UserCoupon;
import cn.lx.worldcoffee.module.shop.dao.UserCouponDao;
import cn.lx.worldcoffee.module.shop.domain.vo.PointBalanceVO;
import cn.lx.worldcoffee.module.shop.domain.vo.PointRecordVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 积分服务
 * 会员等级阈值：0/500/2000/5000/10000 对应 1-5级
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PointService {

    private final PointRecordDao pointRecordDao;
    private final PointExchangeRuleDao ruleDao;
    private final SysUserDao sysUserDao;
    private final UserCouponDao userCouponDao;

    private static final int[] LEVEL_THRESHOLDS = {0, 500, 2000, 5000, 10000};
    private static final String[] LEVEL_NAMES = {"普通会员", "白银会员", "黄金会员", "铂金会员", "钻石会员"};

    /**
     * 消费获得积分（订单支付回调时调用）
     * 1元 = 1积分，向下取整
     */
    @Transactional(rollbackFor = Exception.class)
    public void earnPointsFromOrder(Long userId, String orderNo, BigDecimal amount) {
        int points = amount.intValue();
        if (points <= 0) return;

        SysUser user = sysUserDao.selectById(userId);
        if (user == null) return;

        int newBalance = (user.getPoints() != null ? user.getPoints() : 0) + points;
        int newTotal = (user.getTotalPoints() != null ? user.getTotalPoints() : 0) + points;
        int newLevel = calculateLevel(newTotal);

        sysUserDao.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPoints, newBalance)
                .set(SysUser::getTotalPoints, newTotal)
                .set(SysUser::getMemberLevel, newLevel));

        PointRecord record = new PointRecord();
        record.setUserId(userId);
        record.setType(1);
        record.setChangeAmount(points);
        record.setBalanceAfter(newBalance);
        record.setSourceType("ORDER");
        record.setRemark("订单消费 " + orderNo);
        record.setCreateTime(LocalDateTime.now());
        pointRecordDao.insert(record);

        if (newLevel > (user.getMemberLevel() != null ? user.getMemberLevel() : 1)) {
            log.info("用户{}会员等级从{}提升到{}", userId,
                    user.getMemberLevel() != null ? user.getMemberLevel() : 1, newLevel);
        }
    }

    /**
     * 评价获得积分（评价提交时调用）
     * 5星+10分，4星+5分，3星及以下不额外加积分
     */
    @Transactional(rollbackFor = Exception.class)
    public void earnPointsFromReview(Long userId, Long reviewId, int rating) {
        int points = switch (rating) {
            case 5 -> 10;
            case 4 -> 5;
            default -> 0;
        };
        if (points <= 0) return;

        SysUser user = sysUserDao.selectById(userId);
        if (user == null) return;

        int newBalance = (user.getPoints() != null ? user.getPoints() : 0) + points;
        int newTotal = (user.getTotalPoints() != null ? user.getTotalPoints() : 0) + points;

        sysUserDao.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPoints, newBalance)
                .set(SysUser::getTotalPoints, newTotal));

        PointRecord record = new PointRecord();
        record.setUserId(userId);
        record.setType(2);
        record.setChangeAmount(points);
        record.setBalanceAfter(newBalance);
        record.setSourceId(reviewId);
        record.setSourceType("REVIEW");
        record.setRemark(rating + "星评价奖励");
        record.setCreateTime(LocalDateTime.now());
        pointRecordDao.insert(record);
    }

    /**
     * 积分兑换优惠券
     */
    @Transactional(rollbackFor = Exception.class)
    public void exchangeCoupon(Long ruleId) {
        Long userId = SecurityUtils.requireUserId();

        PointExchangeRule rule = ruleDao.selectById(ruleId);
        if (rule == null || rule.getStatus() != 1)
            throw new ServiceException("兑换规则不存在或已停用");

        SysUser user = sysUserDao.selectById(userId);
        if (user == null) throw new ServiceException("用户不存在");
        int currentPoints = user.getPoints() != null ? user.getPoints() : 0;
        if (currentPoints < rule.getRequiredPoints())
            throw new ServiceException("积分不足，当前积分：" + currentPoints);

        // 扣减积分
        int newBalance = currentPoints - rule.getRequiredPoints();
        sysUserDao.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPoints, newBalance));

        // 发放优惠券
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(rule.getCouponId());
        uc.setUsed(0);
        uc.setCreateTime(LocalDateTime.now());
        userCouponDao.insert(uc);

        // 插入积分流水
        PointRecord record = new PointRecord();
        record.setUserId(userId);
        record.setType(3);
        record.setChangeAmount(-rule.getRequiredPoints());
        record.setBalanceAfter(newBalance);
        record.setSourceId(rule.getCouponId());
        record.setSourceType("EXCHANGE");
        record.setRemark("兑换优惠券：" + rule.getName());
        record.setCreateTime(LocalDateTime.now());
        pointRecordDao.insert(record);
    }

    /**
     * 查询积分余额和等级
     */
    public PointBalanceVO getBalance() {
        Long userId = SecurityUtils.requireUserId();
        SysUser user = sysUserDao.selectById(userId);
        if (user == null) throw new ServiceException("用户不存在");

        int level = user.getMemberLevel() != null ? user.getMemberLevel() : 1;
        return PointBalanceVO.builder()
                .points(user.getPoints() != null ? user.getPoints() : 0)
                .totalPoints(user.getTotalPoints() != null ? user.getTotalPoints() : 0)
                .memberLevel(level)
                .memberLevelDesc(LEVEL_NAMES[level - 1])
                .build();
    }

    /**
     * 查询积分流水
     */
    public List<PointRecordVO> myRecords(int page, int size) {
        Long userId = SecurityUtils.requireUserId();
        List<PointRecord> records = pointRecordDao.selectList(
                new LambdaQueryWrapper<PointRecord>()
                        .eq(PointRecord::getUserId, userId)
                        .orderByDesc(PointRecord::getCreateTime)
                        .last("LIMIT " + (page - 1) * size + "," + size));

        String[] typeDescs = {"", "消费获得", "评价获得", "兑换消耗", "退款扣除", "管理员调整"};
        return records.stream().map(r -> PointRecordVO.builder()
                .id(r.getId())
                .type(r.getType())
                .typeDesc(r.getType() >= 1 && r.getType() <= 5 ? typeDescs[r.getType()] : "未知")
                .changeAmount(r.getChangeAmount())
                .balanceAfter(r.getBalanceAfter())
                .sourceType(r.getSourceType())
                .remark(r.getRemark())
                .createTime(r.getCreateTime())
                .build()).collect(Collectors.toList());
    }

    /**
     * 查询可兑换的规则列表
     */
    public List<PointExchangeRule> listExchangeRules() {
        return ruleDao.selectList(new LambdaQueryWrapper<PointExchangeRule>()
                .eq(PointExchangeRule::getStatus, 1)
                .orderByAsc(PointExchangeRule::getRequiredPoints));
    }

    /**
     * 计算会员等级
     */
    private int calculateLevel(int totalPoints) {
        int level = 1;
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (totalPoints >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
                break;
            }
        }
        return level;
    }
}
