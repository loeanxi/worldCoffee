package cn.lx.worldcoffee.user.domain.from;

import lombok.Data;

@Data
public class UpdateProfileFrom {
    private String username;
    private String avatar;
    private String phone;
}
