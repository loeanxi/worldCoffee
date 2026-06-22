package cn.lx.worldcoffee.module.user.service;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.common.security.JwtUtil;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import cn.lx.worldcoffee.module.user.domain.form.LoginFrom;
import cn.lx.worldcoffee.module.user.domain.form.RegisterForm;
import cn.lx.worldcoffee.module.user.domain.vo.LoginVO;
import cn.lx.worldcoffee.module.user.domain.vo.ReturnMeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public LoginVO login(LoginFrom from) {

        // 1. 根据用户名查询用户
        User user = userDao.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, from.getUsername()));
        // 2. 用户不存在
        if (user == null){
            throw new RuntimeException("用户名不存在");
        }
        // 3. 校验密码
        if (!passwordEncoder.matches(from.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        // 4. 校验账号状态
        if (user.getStatus() == 0){
            throw new RuntimeException("您的账号被冻结 请联系管理员");
        }
        // 5. 生成JWT令牌
        String token = jwtUtil.generateToken(user.getId().toString(), user.getUsername());
        // 6. 返回登录信息
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    public ReturnMeVO ReturnMe() {
        // 1. 从 Spring Security 登记簿里拿当前登录用户的 userId
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = Long.valueOf(auth.getPrincipal().toString());
        // 2. 用 userId 查数据库
        User user = userDao.selectById(userId);
        if (user == null){
            throw new RuntimeException("用户不存在");
        }
        // 3. Entity → VO（丢掉 password，只返回安全字段）
        return ReturnMeVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }
}
