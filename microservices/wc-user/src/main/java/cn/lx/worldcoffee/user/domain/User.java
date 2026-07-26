package cn.lx.worldcoffee.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @JsonIgnore
    private String password;
    private String phone;
    private Integer status;
    private String avatar;
    private Integer points;         // 可用积分
    private Integer totalPoints;    // 累计积分
    private Integer memberLevel;    // 会员等级 1普通 2白银 3黄金 4铂金 5钻石
    private LocalDateTime createTime;
}
