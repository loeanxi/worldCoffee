package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水VO
 */
@Data
@Builder
public class PointRecordVO {
    private Long id;
    private Integer type;
    private String typeDesc;
    private Integer changeAmount;
    private Integer balanceAfter;
    private String sourceType;
    private String remark;
    private LocalDateTime createTime;
}
