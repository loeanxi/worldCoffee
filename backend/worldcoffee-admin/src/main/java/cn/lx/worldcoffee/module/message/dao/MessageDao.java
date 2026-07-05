package cn.lx.worldcoffee.module.message.dao;

import cn.lx.worldcoffee.module.message.domain.PrivateMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper // 告诉 Spring：这是一个 MyBatis 的接口，启动时自动生成实现类
public interface MessageDao extends BaseMapper<PrivateMessage> {
    //extends BaseMapper<PrivateMessage> 给你免费送了哪些方法？
    /**
     * insert(msg)	INSERT INTO private_message ...
     * selectById(id)	SELECT * FROM private_message WHERE id = ?
     * selectList(wrapper)	SELECT * FROM private_message WHERE ...
     * updateById(msg)	UPDATE private_message SET ... WHERE id = ?
     * deleteById(id)	DELETE FROM private_message WHERE id = ?
     * 你不用写一行 SQL，MyBatis-Plus 全帮你干了。
     */
}
