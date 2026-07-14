package cn.lx.worldcoffee.community.dao;

import cn.lx.worldcoffee.community.domain.CoffeeComment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CoffeeCommentDao extends BaseMapper<CoffeeComment> {
}
