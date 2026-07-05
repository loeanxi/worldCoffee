package cn.lx.worldcoffee.module.coffee.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.coffee.domain.from.CommentCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.from.PostCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.from.ReportCreatFrom;
import cn.lx.worldcoffee.module.coffee.domain.vo.CommentVO;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostDetailVO;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostListVO;
import cn.lx.worldcoffee.module.coffee.service.CoffeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Delete;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "咖啡帖子模块", description = "帖子 CRUD、点赞收藏、评论、图片上传、搜索")
@RestController
@RequestMapping("/api/coffee")
@RequiredArgsConstructor
public class CoffeeController {

    private final CoffeeService coffeeService;

    @Operation(summary = "首页帖子列表", description = "分页获取所有正常帖子，按时间倒序")
    /**
     * 首页列表 - 分页瀑布流
     * GET /api/coffee/posts?page=1&size=10
     */
    @GetMapping("/posts")
    public Result<List<PostListVO>> listPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size){
        return Result.success(coffeeService.listPosts(page,size));
    }

    @Operation(summary = "发帖", description = "创建图文分享或打卡记录，需要登录")
    /**
     * 发帖（图文/打卡）
     * POST /api/coffee/posts
     * 需要登录：JWT Token 自动带当前用户ID
     * 前端传 title/content/images/coffeeName/coffeeBrand/location/postType
     */
    @PostMapping("/posts")
    public Result<Void> createPost(@Valid @RequestBody PostCreateFrom from){
        coffeeService.createPost(from);
        return Result.success(null);
    }

    @Operation(summary = "帖子详情", description = "获取帖子的完整信息 + 评论列表 + 当前用户点赞收藏状态")
    /**
     * 帖子详情 + 评论列表
     * GET /api/coffee/posts/{id}
     * 公开接口，不需要登录
     * 返回：帖子全部信息 + 评论列表（每条评论带评论人名字） + 当前用户是否已点赞/收藏
     */
    @GetMapping("/posts/{id}")
    public Result<PostDetailVO> postDetail(
            @Parameter(description = "帖子ID") @PathVariable Long id){
        return Result.success(coffeeService.getPostDetail(id));
    }

    @Operation(summary = "搜索帖子", description = "模糊匹配标题、咖啡名、品牌、内容四个字段")
    /**
     * 搜索帖子
     * GET /api/coffee/search?keyword=手冲&page=1&size=10
     * 公开接口，不需要登录
     * 模糊匹配标题、咖啡名、品牌、内容四个字段
     */
    @GetMapping("/search")
    public Result<List<PostListVO>> search(
            @Parameter(description = "搜索关键词") @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size){
        return Result.success(coffeeService.search(keyword,page,size));
    }

    @Operation(summary = "点赞/取消点赞", description = "toggle 模式，返回 true=已点赞 false=已取消")
    /**
     * 点赞/取消点赞
     * POST /api/coffee/posts/{id}/like
     * 需要登录
     * 返回 true=点赞成功 false=取消点赞
     */
    @PostMapping("/posts/{id}/like")
    public Result<Boolean> toggleLike(
            @Parameter(description = "帖子ID") @PathVariable Long id){
        return Result.success(coffeeService.toggleLike(id));
    }

    @Operation(summary = "收藏/取消收藏", description = "toggle 模式，返回 true=已收藏 false=已取消")
    @PostMapping("/posts/{id}/favorite")
    public Result<Boolean> toggleFavorite(
            @Parameter(description = "帖子ID") @PathVariable Long id){
        return Result.success(coffeeService.toggleFavorite(id));
    }

    @Operation(summary = "发表评论", description = "对帖子发表评论，需要登录")
    /**
     * 发表评论
     * POST /api/coffee/posts/{id}/comment
     * 需要登录
     */
    //校验登录 → 校验帖子存在 → INSERT 评论 → UPDATE 评论数+1。没有 toggle 逻辑，比点赞/收藏还简单。
    @PostMapping("/posts/{id}/comment")
    public Result<CommentVO> addComment(
            @Parameter(description = "帖子ID") @PathVariable Long id,
            @Valid @RequestBody CommentCreateFrom from){
        return Result.success(coffeeService.addComment(id,from));
    }

    @Operation(summary = "删除帖子", description = "软删除，只能删自己的帖子")
    /**
     * 删除帖子
     * DELETE /api/coffee/posts/{id}
     * 需要登录
     */
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(
            @Parameter(description = "帖子ID") @PathVariable Long id){
        coffeeService.deletePost(id);
        return Result.success(null);
    }

    @Operation(summary = "修改帖子", description = "只能修改自己发布的帖子")
    /**
     * 修改帖子
     * PUT /api/coffee/posts/{id}
     * 需要登录
     */
    @PutMapping("/posts/{id}")
    public Result<Void> updatePost(
            @Parameter(description = "帖子ID") @PathVariable Long id,
            @Valid @RequestBody PostCreateFrom from){
        coffeeService.updatePost(id,from);
        return Result.success(null);
    }

    @Operation(summary = "删除评论", description = "评论作者或帖子作者都可以删除")
    /**
     * 删除评论
     * DELETE /api/coffee/comments/{id}
     * 需要登录
     */
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(
            @Parameter(description = "评论ID") @PathVariable Long id){
        coffeeService.deleteComment(id);
        return Result.success(null);
    }

    @Operation(summary = "我的帖子", description = "查看当前用户发布的帖子列表")
    @GetMapping("/posts/my")
    public Result<List<PostListVO>> getMyPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size){
        return Result.success(coffeeService.getMyPosts(page,size));
    }

    @Operation(summary = "我的收藏", description = "查看当前用户收藏的帖子列表")
    @GetMapping("/favorites/my")
    public Result<List<PostListVO>> getMyFavorites(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(coffeeService.getMyFavorites(page, size));
    }

    @Operation(summary = "我的点赞", description = "查看当前用户点赞过的帖子列表")
    @GetMapping("/likes/my")
    public Result<List<PostListVO>> getMyLikes(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(coffeeService.getMyLikes(page, size));
    }

    @Operation(summary = "上传图片", description = "上传图片文件，返回可访问的 URL 地址")
    /**
     * 上传图片
     * POST /api/coffee/upload
     * 需要登录
     * 前端用 FormData 传：formData.append('file', file)
     */
    @PostMapping(value = "/upload",produces = "application/json")
    public Result<String> uploadImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        return Result.success(coffeeService.uploadImage(file));
    }

    @Operation(summary = "热门帖子", description = "按 like_count + comment_count + favorite_count 综合排序")
    @GetMapping("/posts/hot")
    public Result<List<PostListVO>> getHotPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(coffeeService.getHotPosts(page, size));
    }

    @Operation(summary = "评论点赞/取消", description = "对评论进行点赞 toggle")
    @PostMapping("/comments/{id}/like")
    public Result<Boolean> toggleCommentLike(
            @Parameter(description = "评论ID") @PathVariable Long id) {
        return Result.success(coffeeService.toggleCommentLike(id));
    }

    @Operation(summary = "关注动态", description = "查看关注的人发布的帖子，按时间倒序")
    /**
     * 关注的人的帖子
     * GET /api/coffee/posts/following?page=1&size=10
     * 需要登录
     */
    @GetMapping("/posts/following")
    public Result<List<PostListVO>> getFollowingPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(coffeeService.getFollowingPosts(page,size));
    }

    @Operation(summary = "举报帖子", description = "举报违规帖子，需要登录，同一人同一帖只能举报一次")
    @PostMapping("/posts/{id}/report")
    public Result<Void> reportPost(
            @Parameter(description = "帖子ID") @PathVariable Long id,
            @Valid @RequestBody ReportCreatFrom from) {
        coffeeService.reportPost(id, from);
        return Result.success(null);
    }
}
