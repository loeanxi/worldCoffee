package cn.lx.worldcoffee.module.shop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coffee_product")
public class CoffeeProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;            // 商品名称
    private String description;     // 商品描述
    private BigDecimal price;       // 价格，用BigDecimal防止精度丢失
    private String images;          // 图片JSON数组字符串
    private String origin;          // 产地
    private String roastLevel;      // 烘焙度：浅/中/深
    private String weight;          // 规格，如200g
    private Integer stock;          // 库存
    private Integer sales;          // 销量
    private Integer status;         // 1-上架 0-下架
    private LocalDateTime createTime;
    private Long categoryId;          //分类id
}
