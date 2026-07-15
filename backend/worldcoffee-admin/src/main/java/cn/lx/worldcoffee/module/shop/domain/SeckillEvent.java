package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seckill_event")
public class SeckillEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long couponId;
    private Integer status; // 0 1 2 3
    private String payload;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}