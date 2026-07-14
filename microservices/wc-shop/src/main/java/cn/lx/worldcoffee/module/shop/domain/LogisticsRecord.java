package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("logistics_record")
public class LogisticsRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;       // 订单ID
    private String status;      // SHIPPED / IN_TRANSIT / OUT_FOR_DELIVERY / DELIVERED
    private String description; // 描述
    private String location;    // 当前位置
    private LocalDateTime createTime;
}
