package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shipping_address")
public class ShippingAddress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String receiverName;    // 收货人
    private String phone;           // 手机号
    private String province;        // 省
    private String city;            // 市
    private String district;        // 区
    private String detail;          // 详细地址
    private Integer isDefault;      // 是否默认 0-否 1-是
    private LocalDateTime createTime;
}
