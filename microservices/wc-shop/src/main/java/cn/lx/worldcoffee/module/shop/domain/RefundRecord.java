package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款记录实体
 */
@Data
@TableName("refund_record")
public class RefundRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String refundNo;
    private String orderNo;
    private Long userId;
    private Integer type;          // 1仅退款 2退货退款
    private String reason;
    private BigDecimal amount;
    private Integer status;        // 0申请中 1审核中 2退款成功 3退款拒绝 4已取消
    private String adminRemark;
    private LocalDateTime handleTime;
    private String trackingNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
