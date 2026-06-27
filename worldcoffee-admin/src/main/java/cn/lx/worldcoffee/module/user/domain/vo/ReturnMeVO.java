package cn.lx.worldcoffee.module.user.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReturnMeVO {
    private Long id;
    private String username;
    private String phone;
    private Integer status;
    private String avatar;  // 头像URL
    private LocalDateTime createTime;
}
