package cn.lx.worldcoffee.module.coffee.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.coffee.domain.from.CommentCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.from.PostCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.vo.CommentVO;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostDetailVO;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostListVO;
import cn.lx.worldcoffee.module.coffee.service.CoffeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Delete;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coffee")
@RequiredArgsConstructor
public class CoffeeController {

    private final CoffeeService coffeeService;

    /**
     * 首页列表 - 分页瀑布流
     * GET /api/coffee/posts?page=1&size=10
     */
    @GetMapping("/posts")
    public Result<List<PostListVO>> listPosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size){
        return Result.success(coffeeService.listPosts(page,size));
    }

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

    /**
     * 帖子详情 + 评论列表
     * GET /api/coffee/posts/{id}
     * 公开接口，不需要登录
     * 返回：帖子全部信息 + 评论列表（每条评论带评论人名字） + 当前用户是否已点赞/收藏
     */
    @GetMapping("/posts/{id}")
    public Result<PostDetailVO> postDetail(@PathVariable Long id){
        return Result.success(coffeeService.getPostDetail(id));
    }

    /**
     * 搜索帖子
     * GET /api/coffee/search?keyword=手冲&page=1&size=10
     * 公开接口，不需要登录
     * 模糊匹配标题、咖啡名、品牌、内容四个字段
     */
    @GetMapping("/search")
    public Result<List<PostListVO>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ){
        return Result.success(coffeeService.search(keyword,page,size));
    }

    /**
     * 点赞/取消点赞
     * POST /api/coffee/posts/{id}/like
     * 需要登录
     * 返回 true=点赞成功 false=取消点赞
     */
    @PostMapping("/posts/{id}/like")
    public Result<Boolean> toggleLike(@PathVariable Long id){
        return Result.success(coffeeService.toggleLike(id));
    }


    @PostMapping("/posts/{id}/favorite")
    public Result<Boolean> toggleFavorite(@PathVariable Long id){
        return Result.success(coffeeService.toggleFavorite(id));
    }

    /**
     * 发表评论
     * POST /api/coffee/posts/{id}/comment
     * 需要登录
     */
    //校验登录 → 校验帖子存在 → INSERT 评论 → UPDATE 评论数+1。没有 toggle 逻辑，比点赞/收藏还简单。
    @PostMapping("/posts/{id}/comment")
    public Result<CommentVO> addComment(@PathVariable Long id,
                                        @Valid @RequestBody CommentCreateFrom from){
        return Result.success(coffeeService.addComment(id,from));
    }


    /**
     * 删除帖子
     * DELETE /api/coffee/posts/{id}
     * 需要登录
     */
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@PathVariable Long id){
        coffeeService.deletePost(id);
        return Result.success(null);
    }

    /**
     * 修改帖子
     * PUT /api/coffee/posts/{id}
     * 需要登录
     */
    @PutMapping("/posts/{id}")
    public Result<Void> updatePost(@PathVariable Long id,@Valid @RequestBody PostCreateFrom from){
        coffeeService.updatePost(id,from);
        return Result.success(null);
    }

    /**
     * 删除评论
     * DELETE /api/coffee/comments/{id}
     * 需要登录
     */
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id){
        coffeeService.deleteComment(id);
        return Result.success(null);
    }

    public Result<Void> getMyPosts() {

        return Result.success(null);
    }

}
