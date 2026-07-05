package cn.lx.worldcoffee.module.notification.dao;

import cn.lx.worldcoffee.module.notification.domain.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationDao extends BaseMapper<Notification> {
}
