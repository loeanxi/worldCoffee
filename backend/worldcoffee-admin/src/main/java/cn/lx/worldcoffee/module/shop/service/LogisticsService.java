package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.module.shop.dao.CoffeeOrderDao;
import cn.lx.worldcoffee.module.shop.dao.LogisticsRecordDao;
import cn.lx.worldcoffee.module.shop.domain.CoffeeOrder;
import cn.lx.worldcoffee.module.shop.domain.LogisticsRecord;
import cn.lx.worldcoffee.module.shop.domain.vo.LogisticsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 物流服务 —— 发货、物流轨迹查询、确认收货。
 *
 * 职责：
 *   1. 发货（管理员操作）：更新订单状态 + 填入快递信息 + 生成 Mock 物流轨迹
 *   2. 查询物流轨迹：按时间倒序展示物流节点
 *   3. 确认收货（用户操作）：订单状态 → 已完成 + 插入签收记录
 *
 * 为什么单独拆出来：
 *   物流是订单的"售后"阶段，跟下单、库存完全不同领域。
 *   它依赖 LogisticsRecordDao，跟商品/购物车/库存没关系。
 *
 * 依赖：
 *   - CoffeeOrderDao：查/改订单状态
 *   - LogisticsRecordDao：物流轨迹表 CRUD
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogisticsService {

    private final CoffeeOrderDao orderDao;
    private final LogisticsRecordDao logisticsRecordDao;

    // ==================== 发货（管理员） ====================

    /**
     * 发货（Mock）。
     *
     * 做了什么：
     *   1. 校验订单存在 + 状态是"已支付"（只有已支付才能发货）
     *   2. 更新订单：状态 → 已发货(2)，填入快递公司 + 单号 + 发货时间
     *   3. 一次性插入 4 条 Mock 物流轨迹（模拟"发货后轨迹已全部生成"）
     *
     * 为什么一次性插 4 条：
     *   真实场景是快递公司回调推送轨迹，这里是 Mock——
     *   模拟"发货后轨迹已经全部生成"，前端直接展示完整时间线。
     *   如果想更真实，可以只插第一条，后面的用定时任务模拟推送。
     *
     * @param orderId        订单 ID
     * @param shippingCompany 快递公司（如"顺丰速运"）
     * @param trackingNo     快递单号
     */
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(Long orderId, String shippingCompany, String trackingNo) {
        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (order.getStatus() != 1) throw new ServiceException("只有已支付的订单才能发货");

        // 1. 更新订单：状态 → 已发货，填入物流信息
        order.setStatus(2);
        order.setShippingCompany(shippingCompany);
        order.setTrackingNo(trackingNo);
        order.setShippedTime(LocalDateTime.now());
        orderDao.updateById(order);

        // 2. 生成 Mock 物流轨迹（模拟真实物流公司的节点）
        LocalDateTime now = LocalDateTime.now();

        LogisticsRecord r1 = new LogisticsRecord();
        r1.setOrderId(orderId);
        r1.setStatus("SHIPPED");
        r1.setDescription("商家已发货，" + shippingCompany + "正在揽收");
        r1.setLocation("发货仓");
        r1.setCreateTime(now);
        logisticsRecordDao.insert(r1);

        LogisticsRecord r2 = new LogisticsRecord();
        r2.setOrderId(orderId);
        r2.setStatus("IN_TRANSIT");
        r2.setDescription("快件已从发货仓发出，正在运输中");
        r2.setLocation("转运中心");
        r2.setCreateTime(now.plusHours(2));
        logisticsRecordDao.insert(r2);

        LogisticsRecord r3 = new LogisticsRecord();
        r3.setOrderId(orderId);
        r3.setStatus("IN_TRANSIT");
        r3.setDescription("快件已到达目的城市分拨中心");
        r3.setLocation("目的地分拨中心");
        r3.setCreateTime(now.plusHours(8));
        logisticsRecordDao.insert(r3);

        LogisticsRecord r4 = new LogisticsRecord();
        r4.setOrderId(orderId);
        r4.setStatus("OUT_FOR_DELIVERY");
        r4.setDescription("快件正在派送中，请保持电话畅通");
        r4.setLocation("派送网点");
        r4.setCreateTime(now.plusHours(12));
        logisticsRecordDao.insert(r4);
    }

    // ==================== 查询物流 ====================

    /**
     * 查询物流轨迹。
     *
     * 逻辑：
     *   1. 校验订单存在 + 属于当前用户
     *   2. 如果还没发货（status < 2），返回 PENDING 状态（没有物流信息）
     *   3. 查物流记录表，按时间倒序（最新的在上面）
     *   4. 判断是否已签收（有没有 DELIVERED 状态的记录）
     *
     * @param orderId 订单 ID
     * @return 物流 VO（快递公司、单号、当前状态、轨迹节点列表）
     */
    public LogisticsVO getLogistics(Long orderId) {
        Long userId = SecurityUtils.requireUserId();

        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权查看该订单物流");

        if (order.getStatus() < 2) {
            // 还没发货，没有物流信息
            return LogisticsVO.builder()
                    .currentStatus("PENDING")
                    .build();
        }

        // 查物流轨迹（按时间倒序，最新的在上面）
        List<LogisticsRecord> records = logisticsRecordDao.selectList(
                new LambdaQueryWrapper<LogisticsRecord>()
                        .eq(LogisticsRecord::getOrderId, orderId)
                        .orderByDesc(LogisticsRecord::getCreateTime));

        List<LogisticsVO.LogisticsNode> nodes = records.stream().map(r ->
                LogisticsVO.LogisticsNode.builder()
                        .status(r.getStatus())
                        .description(r.getDescription())
                        .location(r.getLocation())
                        .createTime(r.getCreateTime())
                        .build()
        ).collect(Collectors.toList());

        // 判断是否已签收
        boolean delivered = records.stream().anyMatch(r -> "DELIVERED".equals(r.getStatus()));

        return LogisticsVO.builder()
                .shippingCompany(order.getShippingCompany())
                .trackingNo(order.getTrackingNo())
                .currentStatus(delivered ? "DELIVERED" : "IN_TRANSIT")
                .nodes(nodes)
                .build();
    }

    // ==================== 确认收货（用户） ====================

    /**
     * 确认收货。
     *
     * 做了什么：
     *   1. 校验订单存在 + 属于当前用户 + 状态是"已发货"(2)
     *   2. 更新订单状态 → 已完成(3)，记录收货时间
     *   3. 插入一条 DELIVERED 物流记录（签收节点）
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceipt(Long orderId) {
        Long userId = SecurityUtils.requireUserId();

        CoffeeOrder order = orderDao.selectById(orderId);
        if (order == null) throw new ServiceException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new ServiceException("无权操作");
        if (order.getStatus() != 2) throw new ServiceException("只有已发货的订单才能确认收货");

        // 1. 更新订单状态 → 已完成
        order.setStatus(3);
        order.setDeliveredTime(LocalDateTime.now());
        orderDao.updateById(order);

        // 2. 插入一条 DELIVERED 物流记录
        LogisticsRecord record = new LogisticsRecord();
        record.setOrderId(orderId);
        record.setStatus("DELIVERED");
        record.setDescription("已签收，感谢使用 World Coffee");
        record.setLocation("签收");
        record.setCreateTime(LocalDateTime.now());
        logisticsRecordDao.insert(record);
    }
}
