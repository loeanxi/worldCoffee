package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水实体
 */
@Data
@TableName("point_record")
public class PointRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer type;        // 1消费获得 2评价获得 3兑换消耗 4退款扣除 5管理员调整
    private Integer changeAmount;
    private Integer balanceAfter;
    private Long sourceId;
    private String sourceType;
    private String remark;
    private LocalDateTime createTime;
}
