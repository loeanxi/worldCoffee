package cn.lx.worldcoffee.module.shop.dao;

import cn.lx.worldcoffee.module.shop.domain.CartItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartItemDao extends BaseMapper<CartItem> {
}
