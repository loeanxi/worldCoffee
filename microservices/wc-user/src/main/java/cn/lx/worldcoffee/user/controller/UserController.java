package cn.lx.worldcoffee.user.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.user.domain.from.*;
import cn.lx.worldcoffee.user.domain.vo.*;
import cn.lx.worldcoffee.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/user", "/api/users"})
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterForm form) {
        return Result.success(userService.register(form));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginFrom form) {
        return Result.success(userService.login(form));
    }

    @GetMapping("/me")
    public Result<ReturnMeVO> me() {
        return Result.success(userService.getMe());
    }

    @RequestMapping(value = {"/profile", "/me"}, method = RequestMethod.PUT)
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileFrom form) {
        userService.updateProfile(form);
        return Result.success(null);
    }

    @RequestMapping(value = {"/password", "/me/password"}, method = {RequestMethod.PUT, RequestMethod.PATCH})
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordFrom form) {
        userService.changePassword(form);
        return Result.success(null);
    }

    @GetMapping("/{id}")
    public Result<UserProfileVO> getUserProfile(@PathVariable Long id) {
        return Result.success(userService.getUserProfile(id));
    }

    @PostMapping("/{id}/follow")
    public Result<Boolean> toggleFollow(@PathVariable Long id) {
        return Result.success(userService.toggleFollow(id));
    }

    @GetMapping("/{id}/following")
    public Result<List<FollowingVO>> followingList(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.getFollowingList(id, page, size));
    }

    @GetMapping("/{id}/followers")
    public Result<List<FollowingVO>> followersList(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getFollowersList(id, page, size));
    }

    @GetMapping("/search")
    public Result<List<FollowingVO>> searchUsers(@RequestParam String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.searchUsers(keyword, page, size));
    }

    @PostMapping(value = "/avatar", produces = "application/json")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(userService.uploadAvatar(file));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        userService.logout(authHeader);
        return Result.success(null);
    }

    @GetMapping("/me/stats")
    public Result<UserStatsVO> myStats() {
        return Result.success(userService.getMyStats());
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestHeader("Authorization") String authHeader) {
        return Result.success(userService.refreshToken(authHeader));
    }

    @DeleteMapping("/me")
    public Result<Void> deleteMe(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        userService.deleteAccount(authHeader);
        return Result.success(null);
    }

    @GetMapping("/batch")
    public Result<Map<Long, UserService.UserInfo>> batchGetUsers(
            @RequestParam(value = "ids", required = false) List<Long> ids,
            @RequestParam(value = "userIds", required = false) List<Long> userIds) {
        List<Long> queryIds = ids != null ? ids : userIds;
        return Result.success(userService.batchGetUsers(queryIds));
    }

    @PostMapping({"/sms-code", "/sms/code"})
    public Result<String> smsCode(@RequestParam String phone) {
        return Result.success(userService.sendSmsCode(phone));
    }

    @PutMapping({"/bind-phone", "/me/phone"})
    public Result<Void> bindPhone(@Valid @RequestBody BindPhoneFrom form) {
        userService.bindPhone(form);
        return Result.success(null);
    }
}
