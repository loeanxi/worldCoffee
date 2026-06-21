package cn.lx.worldcoffee.module.user.service;

import cn.lx.worldcoffee.common.security.JwtUtil;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import cn.lx.worldcoffee.module.user.domain.form.RegisterForm;
import cn.lx.worldcoffee.module.user.domain.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterForm form) {
        // 1. 校验用户名是否重复
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, form.getUsername());
        if (userDao.selectCount(wrapper) > 0) {
            throw new RuntimeException("用户名重复");
        }
        // 2. 手机号唯一校验
        wrapper.clear();
        wrapper.eq(User::getPhone, form.getPhone());
        if (userDao.selectCount(wrapper) > 0) {
            throw new RuntimeException("手机号已注册");
        }
        // 3. 密码加密
        String encode = passwordEncoder.encode(form.getPassword());
        // 4. 组装用户数据入库
        User user = new User();
        user.setUsername(form.getUsername());
        user.setPassword(encode);
        user.setPhone(form.getPhone());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userDao.insert(user);
        // 5. 生成JWT
        String token = jwtUtil.generateToken(user.getId().toString(), user.getUsername());
        // 6. 返回登录VO
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }
}
