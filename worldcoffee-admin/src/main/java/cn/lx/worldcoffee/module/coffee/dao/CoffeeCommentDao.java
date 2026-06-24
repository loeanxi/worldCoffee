package cn.lx.worldcoffee.module.coffee.dao;

import cn.lx.worldcoffee.module.coffee.domain.CoffeeComment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CoffeeCommentDao extends BaseMapper<CoffeeComment> {
}
