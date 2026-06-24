package cn.lx.worldcoffee.module.coffee.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.module.coffee.dao.CoffeeFavoriteDao;
import cn.lx.worldcoffee.module.coffee.dao.CoffeeLikeDao;
import cn.lx.worldcoffee.module.coffee.dao.CoffeePostDao;
import cn.lx.worldcoffee.module.coffee.domain.CoffeeLike;
import cn.lx.worldcoffee.module.coffee.domain.CoffeePost;
import cn.lx.worldcoffee.module.coffee.domain.from.PostCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostListVO;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import cn.lx.worldcoffee.module.coffee.domain.CoffeeLike;
import cn.lx.worldcoffee.module.coffee.domain.CoffeeFavorite;


import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoffeeService {
    private final CoffeePostDao postDao;
    private final UserDao userDao;
    private final CoffeeLikeDao likeDao;
    private final CoffeeFavoriteDao favoriteDao;


    public List<PostListVO> listPosts(Integer page, Integer size) {
        // ===== 1. 分页查帖子 =====
        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeePost::getStatus,1)     //只查正常帖子
                .orderByDesc(CoffeePost::getCreateTime)  //最新在前
                .last("LIMIT " + (page - 1) * size + "," + size);//分页
        List<CoffeePost> posts = postDao.selectList(wrapper);
        if (posts.isEmpty()){
            return List.of();
        }

        // 错误：LIMIT 和数字连在一起，SQL会变成 LIMIT0,10
        //.last("LIMIT" + (page - 1) * size + "," + size);
        //
        //// 正确：加一个空格
        //.last("LIMIT " + (page - 1) * size + "," + size);

        // ===== 2. 批量查发帖人信息 =====
        // 收集所有不重复的 userId
        List<Long> userIds = posts.stream()
                .map(CoffeePost::getUserId)
                .distinct()
                .collect(Collectors.toList());
        /** stream()：把帖子集合转为流式处理；
         map(CoffeePost::getUserId)：遍历每篇帖子，提取发帖人的userId；
         distinct()：对用户 ID 去重，同一个用户发多条帖子只保留一个 ID，避免重复查询；
         collect(Collectors.toList())：把去重后的 ID 收集成集合。*/

        // 一次查出所有用户（避免 N+1 查询）
        Map<Long, User> userMap = userDao.selectBatchIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        /** selectBatchIds(userIds)：MyBatis-Plus 批量查询，执行一条SELECT * FROM user WHERE id IN (?,?,?)SQL，一次性查出所有发帖用户；
         再通过流转换成Map<用户ID, 用户实体>结构；
         后续遍历帖子时，直接用userMap.get(post.getUserId())就能拿到对应用户信息。
         核心作用：解决 N+1 查询问题
         如果不批量查，循环每篇帖子单独根据userId查用户：
         1 次查帖子 + N 次查用户 = N+1 条 SQL，数据量大时性能极差。
         现在只执行2 次 SQL：查帖子、批量查用户，大幅提升查询效率。
         * */

        // ===== 3. 查当前用户是否点赞/收藏 =====
        // 先判断是否登录（SecurityContext 有没有 userId
        Long currentUserId = getCurrentUserId();   // 没登录返回null
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> favoritedPostIds = new HashSet<>();

        if (currentUserId != null) {
            List<Long> postIds = posts.stream()
                    .map(CoffeePost::getId)
                    .collect(Collectors.toList());

            // 查当前用户对这些帖子的点赞记录
            likedPostIds = likeDao.selectList(
                    new LambdaQueryWrapper<CoffeeLike>()
                            .eq(CoffeeLike::getUserId, currentUserId)
                            .in(CoffeeLike::getPostId, postIds)
            ).stream().map(CoffeeLike::getUserId).collect(Collectors.toSet());


            // 查当前用户对这些帖子的收藏记录
            favoritedPostIds = favoriteDao.selectList(
                    new LambdaQueryWrapper<CoffeeFavorite>()
                            .eq(CoffeeFavorite::getUserId, currentUserId)
                            .in(CoffeeFavorite::getPostId, postIds)
            ).stream().map(CoffeeFavorite::getPostId).collect(Collectors.toSet());
        }
        // ===== 4. 组装 PostListVO =====
        final Set<Long> finalLiked = likedPostIds;
        final Set<Long> finalFavorited = favoritedPostIds;

        return posts.stream().map(post -> {
            User user = userMap.get(post.getUserId());
            return PostListVO.builder()
                    .id(post.getId())
                    .userId(post.getUserId())
                    .username(user != null ? user.getUsername() : "未知用户")
                    .title(post.getTitle())
                    .content(truncate(post.getContent(), 80))  // 截取前80字
                    .images(parseImages(post.getImages()))      // JSON字符串→List
                    .coffeeName(post.getCoffeeName())
                    .coffeeBrand(post.getCoffeeBrand())
                    .location(post.getLocation())
                    .postType(post.getPostType())
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .favoriteCount(post.getFavoriteCount())
                    .likedByMe(finalLiked.contains(post.getId()))
                    .favoritedByMe(finalFavorited.contains(post.getId()))
                    .createTime(post.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    // ===== 工具方法 =====
    /** 获取当前登录用户ID，未登录返回null */
    private Long getCurrentUserId(){
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null){
                return Long.valueOf(auth.getPrincipal().toString());
            }
        }catch (Exception ignored){}
        return null;
    }
    /** 截取文本前N个字 */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    /** JSON字符串 → List<String> */
    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return List.of();
        return JSONUtil.toList(imagesJson, String.class);  // Hutool
    }


    public void createPost(PostCreateFrom from) {


    }
}
