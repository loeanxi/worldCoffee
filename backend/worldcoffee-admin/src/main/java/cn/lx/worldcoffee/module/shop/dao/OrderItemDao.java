package cn.lx.worldcoffee.module.shop.dao;

import cn.lx.worldcoffee.module.shop.domain.OrderItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemDao extends BaseMapper<OrderItem> {
}
