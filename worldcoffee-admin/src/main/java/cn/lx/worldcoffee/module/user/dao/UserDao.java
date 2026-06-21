package cn.lx.worldcoffee.module.user.dao;

import cn.lx.worldcoffee.module.user.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserDao extends BaseMapper<User> {
}
