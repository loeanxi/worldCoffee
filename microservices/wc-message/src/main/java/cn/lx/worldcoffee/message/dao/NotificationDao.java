package cn.lx.worldcoffee.message.dao;

import cn.lx.worldcoffee.message.domain.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationDao extends BaseMapper<Notification> {
}
