package cn.lx.worldcoffee.module.shop.domain.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductForm {
    @NotBlank(message = "商品名不能为空")
    private String name;

    private String description;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    private String images;      // JSON 数组字符串，如 ["a.jpg","b.jpg"]
    private String origin;
    private String roastLevel;
    private String weight;
    private Integer stock;
    private Integer sales;
    private Integer status;     // 1-上架 0-下架
}
