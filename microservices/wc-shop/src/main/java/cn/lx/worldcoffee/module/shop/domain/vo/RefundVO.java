package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款展示VO
 */
@Data
@Builder
public class RefundVO {
    private Long id;
    private String refundNo;
    private String orderNo;
    private Integer type;
    private String typeDesc;
    private String reason;
    private BigDecimal amount;
    private Integer status;
    private String statusDesc;
    private String adminRemark;
    private LocalDateTime handleTime;
    private String trackingNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
