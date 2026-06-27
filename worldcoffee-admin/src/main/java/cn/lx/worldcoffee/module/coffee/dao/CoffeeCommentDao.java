package cn.lx.worldcoffee.module.coffee.dao;

import cn.lx.worldcoffee.module.coffee.domain.CoffeeComment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

//你的 CoffeeCommentLikeDao 放在 module/coffee/dao/ 下，@MapperScan 扫包时会自动注册，
//所以不加 @Mapper 也不会报错。但这属于"靠全局配置兜底"，加不加 @Mapper 都能跑，加了更明显。
@Mapper
public interface CoffeeCommentDao extends BaseMapper<CoffeeComment> {
}
