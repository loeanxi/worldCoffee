package cn.lx.worldcoffee.module.user.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.user.domain.form.RegisterForm;
import cn.lx.worldcoffee.module.user.domain.vo.LoginVO;
import cn.lx.worldcoffee.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterForm form) {
        LoginVO vo = userService.register(form);
        return Result.success(vo);
    }
}
