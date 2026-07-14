package cn.lx.worldcoffee.user.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.user.dao.UserDao;
import cn.lx.worldcoffee.user.domain.User;
import cn.lx.worldcoffee.user.domain.from.*;
import cn.lx.worldcoffee.user.domain.vo.LoginVO;
import cn.lx.worldcoffee.user.domain.vo.ReturnMeVO;
import cn.lx.worldcoffee.user.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserDao userDao;
    private final SmsService smsService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 注册 */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterForm form) {
        // 检查用户名是否已存在
        Long count = userDao.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, form.getUsername()));
        if (count > 0) throw new ServiceException("用户名已被注册");

        User user = new User();
        user.setUsername(form.getUsername());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setPhone(form.getPhone());
        user.setStatus(1);
        userDao.insert(user);

        String token = JwtUtil.createToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    /** 登录 */
    public LoginVO login(LoginFrom form) {
        User user = userDao.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, form.getUsername()));
        if (user == null) throw new ServiceException("用户名或密码错误");
        if (!passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            throw new ServiceException("用户名或密码错误");
        }
        String token = JwtUtil.createToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    /** 获取当前用户信息 */
    public ReturnMeVO getMe() {
        Long userId = SecurityUtils.requireUserId();
        User user = userDao.selectById(userId);
        if (user == null) throw new ServiceException("用户不存在");
        return ReturnMeVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .status(user.getStatus())
                .avatar(user.getAvatar())
                .createTime(user.getCreateTime())
                .build();
    }

    /** 更新个人信息 */
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(UpdateProfileFrom form) {
        Long userId = SecurityUtils.requireUserId();
        User user = userDao.selectById(userId);
        if (user == null) throw new ServiceException("用户不存在");
        if (form.getAvatar() != null) user.setAvatar(form.getAvatar());
        if (form.getPhone() != null) user.setPhone(form.getPhone());
        userDao.updateById(user);
    }

    /** 修改密码 */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordFrom form) {
        Long userId = SecurityUtils.requireUserId();
        User user = userDao.selectById(userId);
        if (user == null) throw new ServiceException("用户不存在");
        if (!passwordEncoder.matches(form.getOldPassword(), user.getPassword())) {
            throw new ServiceException("旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        userDao.updateById(user);
    }

    /** 绑定手机号 */
    @Transactional(rollbackFor = Exception.class)
    public void bindPhone(BindPhoneFrom form) {
        Long userId = SecurityUtils.requireUserId();
        if (!smsService.verifySmsCode(form.getPhone(), form.getCode())) {
            throw new ServiceException("验证码错误或已过期");
        }
        User user = userDao.selectById(userId);
        if (user == null) throw new ServiceException("用户不存在");
        user.setPhone(form.getPhone());
        userDao.updateById(user);
    }

    /** 发送短信验证码 */
    public void sendSmsCode(String phone) {
        smsService.sendSmsCode(phone);
    }

    /** 批量获取用户信息（供其他服务 Feign 调用） */
    public Map<Long, UserInfo> batchGetUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<User> users = userDao.selectBatchIds(ids);
        return users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> new UserInfo(u.getId(), u.getUsername(), u.getAvatar())
                ));
    }

    public record UserInfo(Long id, String username, String avatar) {}
}
