package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeckillOrderResultVO {
    private String orderNo;
    private String status;  // PROCESSING / 你定义
}
