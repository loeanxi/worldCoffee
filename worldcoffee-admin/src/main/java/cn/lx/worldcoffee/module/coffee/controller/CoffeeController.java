package cn.lx.worldcoffee.module.coffee.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.coffee.domain.from.PostCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostListVO;
import cn.lx.worldcoffee.module.coffee.service.CoffeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/posts")
    public Result<Void> createPost(@Valid @RequestBody PostCreateFrom from){
        coffeeService.createPost(from);
        return Result.success(null);
    }


}
