package cn.lx.worldcoffee.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnMeVO {
    private Long id;
    private String username;
    private String phone;
    private Integer status;
    private String avatar;
    private LocalDateTime createTime;
}
