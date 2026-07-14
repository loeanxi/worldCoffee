package cn.lx.worldcoffee.module.shop.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法订单号生成器
 */
@Component
public class OrderNoGenerator {

    private final Snowflake snowflake;

    public OrderNoGenerator(@Value("${snowflake.machine-id:1}") long machineId,
                            @Value("${snowflake.data-center-id:1}") long dataCenterId) {
        this.snowflake = IdUtil.getSnowflake(machineId, dataCenterId);
    }

    /**
     * 生成下一个订单号
     */
    public String nextOrderNo() {
        return snowflake.nextIdStr();
    }
}