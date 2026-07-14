package cn.lx.worldcoffee.admin.dao;

import cn.lx.worldcoffee.admin.domain.AdminOperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminOperationLogDao extends BaseMapper<AdminOperationLog> {
}
