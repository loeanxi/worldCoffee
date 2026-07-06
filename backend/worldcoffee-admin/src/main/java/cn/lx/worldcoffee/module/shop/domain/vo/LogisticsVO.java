package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LogisticsVO {
    private String shippingCompany;  // 快递公司
    private String trackingNo;       // 快递单号
    private String currentStatus;    // 当前状态
    private List<LogisticsNode> nodes; // 物流轨迹节点

    @Data
    @Builder
    public static class LogisticsNode {
        private String status;       // 节点状态
        private String description;  // 描述
        private String location;     // 位置
        private LocalDateTime createTime; // 时间
    }
}
