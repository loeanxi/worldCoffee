package cn.lx.worldcoffee.module.shop.service;

import cn.lx.worldcoffee.module.shop.domain.CoffeeOrder;
import cn.lx.worldcoffee.module.shop.domain.PaymentRecord;
import cn.lx.worldcoffee.module.shop.domain.PointExchangeRule;
import cn.lx.worldcoffee.module.shop.domain.PointRecord;
import cn.lx.worldcoffee.module.shop.domain.ProductReview;
import cn.lx.worldcoffee.module.shop.domain.RefundRecord;
import cn.lx.worldcoffee.module.shop.domain.SysUser;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;

/**
 * 纯 Mockito 单测的 MyBatis-Plus TableInfo 预热基类。
 *
 * Service 内使用 LambdaUpdateWrapper/LambdaQueryWrapper 依赖实体的
 * lambda 列缓存，该缓存正常由 MyBatis-Plus 随 Spring 启动注册 Mapper
 * 时初始化；纯单测没有该流程，会抛
 * "can not find lambda cache for this entity"。
 * 这里在 @BeforeAll 手动初始化涉及的实体，幂等可重复调用。
 */
public abstract class MpTableInfoSupport {

    @BeforeAll
    static void initMpLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, PointRecord.class);
        TableInfoHelper.initTableInfo(assistant, PointExchangeRule.class);
        TableInfoHelper.initTableInfo(assistant, CoffeeOrder.class);
        TableInfoHelper.initTableInfo(assistant, RefundRecord.class);
        TableInfoHelper.initTableInfo(assistant, ProductReview.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRecord.class);
    }
}
