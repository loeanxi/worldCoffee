package cn.lx.worldcoffee.module.shop.domain.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminLoginVO {
    private String token;
    private String username;
}
