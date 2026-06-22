package cn.lx.worldcoffee.module.user.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.user.domain.form.LoginFrom;
import cn.lx.worldcoffee.module.user.domain.form.RegisterForm;
import cn.lx.worldcoffee.module.user.domain.vo.LoginVO;
import cn.lx.worldcoffee.module.user.domain.vo.ReturnMeVO;
import cn.lx.worldcoffee.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginFrom from){
        LoginVO vo = userService.login(from);
        return Result.success(vo);
    }

    @GetMapping("/me")
    public Result<ReturnMeVO> returnMe(){
         ReturnMeVO returnMeVO = userService.ReturnMe();
         return Result.success(returnMeVO);
    }
}
