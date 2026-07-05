package cn.lx.worldcoffee.module.shop.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryVO {
    private Long id;
    private String name;
}
