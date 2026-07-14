package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_coupon")
public class UserCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long couponId;
    private Integer used;
    // 0-未使用 1-已使用
    //满减券	用户领了，下单时抵扣， initially used=0
    //折扣券	用户领了，下单时抵扣， initially used=0
    // 秒杀卷：用户抢券 = 立即下单，所以 used=1
    //为什么秒杀券是 used=1
    //秒杀流程是：抢券 + 下单一步完成。
    //
    //用户点秒杀按钮时：
    //
    //领一张秒杀券
    //同时创建订单
    //券立刻被消耗掉
    //所以 UserCoupon 一创建就是"已使用"状态，代表这张券已经绑定到这笔秒杀订单了。
    //
    //如果秒杀成功但订单创建失败（比如 MQ 消费失败进入死信队列），那这张券的状态就需要人工补偿处理。
    //在异步流程里的意义
    //改成 MQ 异步之后，used=1 更早地标记了"这个用户已经参与了这次秒杀"，即使订单还在异步创建中，也能防止用户重复点击。
    //
    //所以这一步是对的，used=1 表示：该用户已占用秒杀资格，券已消耗。
    private LocalDateTime createTime;
}
