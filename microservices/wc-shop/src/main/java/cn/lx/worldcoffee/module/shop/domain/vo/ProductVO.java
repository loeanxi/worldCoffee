package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductVO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String images;          // 前端自己parse成数组
    private String origin;
    private String roastLevel;      // 浅/中/深
    private String weight;          // 200g
    private Integer stock;
    private Integer sales;          // 销量，用于"已售xx件"展示
    private Integer status;         // 上下架状态：0-下架 1-上架（admin 后台商品列表需要）

}
