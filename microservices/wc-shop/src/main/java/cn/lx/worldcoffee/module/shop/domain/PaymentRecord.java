package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String transactionId;
    private BigDecimal amount;
    private Integer status;         // 0-待支付 1-成功 2-失败
    private LocalDateTime createTime;
    private LocalDateTime payTime;
}
