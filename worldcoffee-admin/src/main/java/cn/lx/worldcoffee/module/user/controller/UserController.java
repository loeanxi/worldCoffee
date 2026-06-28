package cn.lx.worldcoffee.module.user.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.user.domain.form.ChangePasswordFrom;
import cn.lx.worldcoffee.module.user.domain.form.LoginFrom;
import cn.lx.worldcoffee.module.user.domain.form.RegisterForm;
import cn.lx.worldcoffee.module.user.domain.form.UpdateProfileFrom;
import cn.lx.worldcoffee.module.user.domain.vo.*;
import cn.lx.worldcoffee.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "用户模块", description = "注册、登录、个人信息、修改密码、关注")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(summary = "注册", description = "创建新账号，返回 JWT token")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterForm form) {
        LoginVO vo = userService.register(form);
        return Result.success(vo);
    }

    @Operation(summary = "登录", description = "用户名 + 密码登录，返回 JWT token")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginFrom from){
        LoginVO vo = userService.login(from);
        return Result.success(vo);
    }

    @Operation(summary = "我的信息", description = "获取当前登录用户的个人信息")
    @GetMapping("/me")
    public Result<ReturnMeVO> returnMe(){
         ReturnMeVO returnMeVO = userService.ReturnMe();
         return Result.success(returnMeVO);
    }

    @Operation(summary = "修改资料", description = "修改当前用户的用户名和手机号")
    /**
     * @PutMapping 对应 HTTP PUT，语义是"整体替换已有资源"。
     * 客户端提交的是资源的完整表示，服务端用这份数据覆盖掉原来的。
     * 它是幂等的——同一个请求发十次，结果和发一次一样，资源状态不变。
     *
     *
     * @PostMapping 对应 HTTP POST，语义是"创建新资源"。
     * 比如用户发一篇新帖子、注册一个新账号，服务端会生成一个新的资源 ID 返回。
     * 它不是幂等的——同样的请求发两次，会创建两条记录。
     */
    @PutMapping("/me")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileFrom from){
        userService.updateProfile(from);
        return Result.success(null);
    }

    @Operation(summary = "用户主页", description = "查看指定用户的公开信息和近期帖子")
    @GetMapping("/{id}")
    public Result<UserProfileVO> getUserFile(
            @Parameter(description = "用户ID") @PathVariable Long id){
        return Result.success(userService.getUserFile(id));
    }

    @Operation(summary = "修改密码")
    @PatchMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordFrom from){
        userService.changePassword(from);
        return Result.success(null);
    }

    @Operation(summary = "关注/取消关注", description = "toggle 模式，关注过则取消，未关注则关注。返回 true=已关注")
    @PostMapping("/{id}/follow")
    public Result<Boolean> toggleFollow(
            @Parameter(description = "被关注用户ID") @PathVariable Long id){
        return Result.success(userService.toggleFollow(id));
    }

    //这个方法是查询某个用户的关注列表，同时标记当前登录用户对列表中每个人的关注状态。
    @Operation(summary = "关注列表", description = "查看指定用户关注了哪些人")
    @GetMapping("/{id}/following")
    public Result<List<FollowingVO>> followingList(
            @Parameter(description = "用户id") @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size
    )
    {
        return Result.success(userService.getFollowingList(id,page,size));
    }
    //关注列表：WHERE follower_id = ?  → 查 .getFolloweeId()
    //粉丝列表：WHERE followee_id = ?  → 查 .getFollowerId()
    @Operation(summary = "粉丝列表", description = "查看有哪些人关注了指定用户")
    @GetMapping("/{id}/followers")
    public Result<List<FollowingVO>> followersList(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.getFollowersList(id, page, size));
    }

    @Operation(summary = "搜索用户", description = "按用户名模糊搜索，返回匹配的用户列表")
    @GetMapping("/search")
    public Result<List<FollowingVO>> searchUsers(
            @Parameter(description = "搜索关键词") @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.searchUsers(keyword, page, size));
    }

    @Operation(summary = "上传头像", description = "上传头像图片，自动更新当前用户的 avatar 字段")
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(
            @Parameter(description = "头像文件") @RequestParam("file") MultipartFile file) {
        return Result.success(userService.uploadAvatar(file));
    }

    //要真正实现登出，得选一个方案。目前的情况：
    //
    //JWT 是无状态的 — token 发出去就管不了了
    //唯一能拦的方式是：把 token 加入黑名单，下次请求时在 JWT 过滤器里拦掉
    @Operation(summary = "登出", description = "使当前 JWT token 失效，清除服务端缓存")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        userService.logout(authHeader);
        return Result.success(null);
    }

    @Operation(summary = "个人统计", description = "当前用户的发帖数、获赞数、收藏数、评论数、关注数、粉丝数")
    @GetMapping("/me/stats")
    public Result<UserStatsVO> myStats() {
        return Result.success(userService.getMyStats());
    }
}
