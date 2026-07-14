package cn.lx.worldcoffee.user.dao;

import cn.lx.worldcoffee.user.domain.CoffeeComment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CoffeeCommentDao extends BaseMapper<CoffeeComment> {
}
