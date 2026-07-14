package cn.lx.worldcoffee.user.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.user.domain.from.*;
import cn.lx.worldcoffee.user.domain.vo.LoginVO;
import cn.lx.worldcoffee.user.domain.vo.ReturnMeVO;
import cn.lx.worldcoffee.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 注册 */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterForm form) {
        return Result.success(userService.register(form));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginFrom form) {
        return Result.success(userService.login(form));
    }

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public Result<ReturnMeVO> me() {
        return Result.success(userService.getMe());
    }

    /** 更新个人信息 */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileFrom form) {
        userService.updateProfile(form);
        return Result.success(null);
    }

    /** 修改密码 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordFrom form) {
        userService.changePassword(form);
        return Result.success(null);
    }

    /** 绑定手机号 */
    @PutMapping("/bind-phone")
    public Result<Void> bindPhone(@Valid @RequestBody BindPhoneFrom form) {
        userService.bindPhone(form);
        return Result.success(null);
    }

    /** 发送短信验证码 */
    @PostMapping("/sms-code")
    public Result<Void> smsCode(@RequestParam String phone) {
        userService.sendSmsCode(phone);
        return Result.success(null);
    }

    /** 批量获取用户信息（供其他服务 Feign 调用） */
    @GetMapping("/batch")
    public Result<Map<Long, UserService.UserInfo>> batchGetUsers(@RequestParam("ids") List<Long> ids) {
        return Result.success(userService.batchGetUsers(ids));
    }
}
