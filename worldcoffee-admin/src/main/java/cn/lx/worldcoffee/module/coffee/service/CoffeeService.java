package cn.lx.worldcoffee.module.coffee.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.module.coffee.dao.CoffeeCommentDao;
import cn.lx.worldcoffee.module.coffee.dao.CoffeeFavoriteDao;
import cn.lx.worldcoffee.module.coffee.dao.CoffeeLikeDao;
import cn.lx.worldcoffee.module.coffee.dao.CoffeePostDao;
import cn.lx.worldcoffee.module.coffee.domain.*;
import cn.lx.worldcoffee.module.coffee.domain.from.CommentCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.from.PostCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.vo.CommentVO;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostDetailVO;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostListVO;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import cn.lx.worldcoffee.module.coffee.domain.CoffeeLike;


import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoffeeService {
    private final CoffeePostDao postDao;
    private final UserDao userDao;
    private final CoffeeLikeDao likeDao;
    private final CoffeeFavoriteDao favoriteDao;
    private final CoffeeCommentDao commentDao;



    public List<PostListVO> listPosts(Integer page, Integer size) {
        // ===== 1. 分页查帖子 =====
        // SQL: SELECT * FROM coffee_post WHERE status = 1 ORDER BY create_time DESC LIMIT 0,10
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
        // SQL: SELECT * FROM sys_user WHERE id IN (1,3,5,7,9...)   ← 一次性查出所有发帖人
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
        // SQL: SELECT * FROM coffee_like WHERE user_id = ? AND post_id IN (1,2,3,...)
        // SQL: SELECT * FROM coffee_favorite WHERE user_id = ? AND post_id IN (1,2,3,...)
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
        // 1. 从 SecurityContext 拿当前登录用户ID
        Long userId = getCurrentUserId();
        if (userId == null){
            throw new RuntimeException("请先登录");
        }
        // 2. 组装 CoffeePost 实体
        CoffeePost post = new CoffeePost();
        post.setUserId(userId);
        post.setTitle(from.getTitle());
        post.setContent(from.getContent());

        //很有意思的一个点，读写操作image的数据流的转换
        //                   【写操作】（发帖）
        //前端传List → Form存List → Service转成JSON字符串 → Entity存String → 数据库存String
        //["a","b"]    ["a","b"]     '["a","b"]'            '["a","b"]'     '["a","b"]'
        //
        //
        //                   【读操作】（列表）
        //数据库存String → Entity存String → Service转回List → VO存List → 前端拿List
        //'["a","b"]'     '["a","b"]'      ["a","b"]       ["a","b"]    ["a","b"]

        // 3. 图片列表 → JSON 字符串存入数据库
        //如果前端传了图片，用 Hutool JSONUtil 转成 JSON 字符串
        if (from.getImages() != null && !from.getImages().isEmpty()){
            post.setImages(JSONUtil.toJsonStr(from.getImages()));
        }
        post.setCoffeeName(from.getCoffeeName());
        post.setCoffeeBrand(from.getCoffeeBrand());
        post.setLocation(from.getLocation());
        post.setPostType(from.getPostType());
        post.setStatus(1);            // 默认上架
        post.setLikeCount(0);         // 初始0赞
        post.setCommentCount(0);      // 初始0评论
        post.setFavoriteCount(0);     // 初始0收藏


        // 4. 入库
        // SQL: INSERT INTO coffee_post (user_id, title, content, images, coffee_name, ...) VALUES (?, ?, ?, ?, ?, ...)
        postDao.insert(post);

        //POST /api/coffee/posts
        //Header: Authorization: Bearer eyJxxx...
        //Body: {"title":"...","coffeeName":"耶加雪菲","postType":1}
        //  │
        //  ▼
        //JwtAuthenticationFilter → 解析token → userId=1 → 设置SecurityContext
        //  │
        //  ▼
        //CoffeeController.createPost(form)
        //  │  @Valid 校验：title不为空？postType不为空？
        //  │
        //  ▼
        //CoffeeService.createPost(form)
        //  │  1. getCurrentUserId() → 从SecurityContext拿userId=1
        //  │  2. new CoffeePost() + 设各字段
        //  │  3. List<String> images → JSONUtil.toJsonStr() → JSON字符串
        //  │  4. postDao.insert(post) → INSERT INTO coffee_post ...
        //  │
        //  ▼
        //返回 {"code":200,"msg":"操作成功","data":null}

    }


    public PostDetailVO getPostDetail(Long postId) {
        // 1. 查帖子
        // SQL: SELECT * FROM coffee_post WHERE id = ?
        CoffeePost post = postDao.selectById(postId);
        if (post == null || post.getStatus() == 0){
            throw new RuntimeException("帖子不存在或者已删除");
        }
        // 2. 查发帖人
        // SQL: SELECT * FROM sys_user WHERE id = ?
        User author = userDao.selectById(post.getUserId());

        // 3. 查评论列表
        // SQL: SELECT * FROM coffee_comment WHERE post_id = ? ORDER BY create_time ASC
        List<CoffeeComment> commentList = commentDao.selectList(new LambdaQueryWrapper<CoffeeComment>()
                .eq(CoffeeComment::getPostId, postId)
                .orderByAsc(CoffeeComment::getCreateTime)
        );

        // 4. 批量查评论人信息
        // SQL: SELECT * FROM sys_user WHERE id IN (2,5,7,...)   ← 批量查评论人
        List<Long> commentIds = commentList.stream()
                .map(CoffeeComment::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long,User> userMap = userDao.selectBatchIds(commentIds)
                .stream()
                .collect(Collectors.toMap(User::getId,u -> u));

        //5.组装评论
        List<CommentVO> commentVOs = commentList.stream().map(c -> {
            User u = userMap.get(c.getUserId());
            return CommentVO.builder()
                    .id(c.getId())
                    .userId(c.getUserId())
                    .username(u != null ? u.getUsername() : "未知")
                    .content(c.getContent())
                    .createTime(c.getCreateTime())
                    .createTime(c.getCreateTime())
                    .build();
        }).collect(Collectors.toList());

        // 6. 查当前用户是否点赞/收藏
        // SQL: SELECT COUNT(*) FROM coffee_like WHERE post_id = ? AND user_id = ?
        // SQL: SELECT COUNT(*) FROM coffee_favorite WHERE post_id = ? AND user_id = ?
        Long currentUserId = getCurrentUserId();
        boolean likedByMe = false;
        boolean favoritedByMe = false;

        if (currentUserId != null){
            likedByMe = likeDao.selectCount(
                    new LambdaQueryWrapper<CoffeeLike>()
                            .eq(CoffeeLike::getPostId,postId)
                            .eq(CoffeeLike::getUserId,currentUserId)
            ) > 0;

            favoritedByMe = favoriteDao.selectCount(
                    new LambdaQueryWrapper<CoffeeFavorite>()
                            .eq(CoffeeFavorite::getPostId,postId)
                            .eq(CoffeeFavorite::getUserId,currentUserId)
            ) > 0;
        }
        // 7. 组装 PostDetailVO
        return PostDetailVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .username(author != null ? author.getUsername() : "未知")
                .title(post.getTitle())
                .content(post.getContent())                      // 完整内容
                .images(parseImages(post.getImages()))            // 复用已有方法
                .coffeeName(post.getCoffeeName())
                .coffeeBrand(post.getCoffeeBrand())
                .location(post.getLocation())
                .postType(post.getPostType())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .favoriteCount(post.getFavoriteCount())
                .likedByMe(likedByMe)
                .favoritedByMe(favoritedByMe)
                .createTime(post.getCreateTime())
                .comments(commentVOs)                             // 评论列表
                .build();
    }
    //GET /api/coffee/posts/1
    //  │
    //  ▼
    //CoffeeService.getPostDetail(1)
    //  │
    //  ├─ SELECT * FROM coffee_post WHERE id = 1                    → 帖子
    //  ├─ SELECT * FROM sys_user WHERE id = userId                  → 发帖人
    //  ├─ SELECT * FROM coffee_comment WHERE post_id = 1            → 评论列表
    //  ├─ SELECT * FROM sys_user WHERE id IN (评论人ID列表)          → 批量查评论人
    //  ├─ SELECT COUNT(*) FROM coffee_like WHERE post_id=1 AND user_id=当前用户  → 是否已点赞
    //  └─ SELECT COUNT(*) FROM coffee_favorite WHERE ...            → 是否已收藏
    //  │
    //  ▼
    //返回 {code:200, data: {帖子详情 + 评论列表 + 点赞收藏状态}}


    //本质：就一件事：查多张表 → 拼成一个对象 → 返回
    //详情接口干的事就是：根据帖子ID，把帖子本身 + 发帖人 + 评论区 + 点赞收藏状态，全部打包成一个对象返回给前端。
    //第1步：查帖子本身（1条SQL） CoffeePost post = postDao.selectById(postId);  // SELECT * FROM coffee_post WHERE id = 1
    //第2步：查发帖人（1条SQL） User author = userDao.selectById(post.getUserId());  // SELECT * FROM sys_user WHERE id = 3
    //因为帖子表里只存了 user_id=3，不知道这人叫什么。去用户表查一下，拿到 username
    //第3步：查评论区（2条SQL）
    //// 3a. 先查这个帖子下有哪些评论
    //List<CoffeeComment> comments = commentDao.selectList(...);
    //// SELECT * FROM coffee_comment WHERE post_id = 1
    //
    //// 3b. 评论里也只有 user_id，批量去查评论人的名字
    //Map<Long, User> userMap = userDao.selectBatchIds(评论人ID列表);
    //// SELECT * FROM sys_user WHERE id IN (2,5,7)
    //第4步：查当前用户是否点过赞/收藏（2条SQL）
    //// 判断当前登录用户有没有给这个帖子点过赞
    //likedByMe = likeDao.selectCount(postId + userId) > 0;


    //GET /api/coffee/posts/1
    //           │
    //  ┌────────┼────────┬────────────┬──────────┐
    //  ▼        ▼        ▼            ▼          ▼
    //查帖子    查发帖人   查评论列表    查是否点赞   查是否收藏
    //(1条SQL)  (1条SQL)  (2条SQL)    (1条SQL)   (1条SQL)
    //  │        │        │            │          │
    //  └────────┴────────┴────────────┴──────────┘
    //                    │
    //                    ▼
    //            全部塞进 PostDetailVO
    //                    │
    //                    ▼
    //            返回给前端


    public List<PostListVO> search(String keyword, Integer page, Integer size) {
        //本质like模糊匹配
        // SQL: SELECT * FROM coffee_post WHERE status = 1
        // AND (title LIKE '%手冲%' OR coffee_name LIKE '%手冲%'
        // OR coffee_brand LIKE '%手冲%' OR content LIKE '%手冲%')
        // ORDER BY create_time DESC LIMIT 0,10
        // 1. 模糊搜索
        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeePost::getStatus,1)
                .and(w -> w.like(CoffeePost::getTitle,keyword))
                .or()
                .like(CoffeePost::getCoffeeName,keyword)
                .or()
                .like(CoffeePost::getCoffeeBrand,keyword)
                .or()
                .like(CoffeePost::getContent,keyword)
                .orderByDesc(CoffeePost::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);

        List<CoffeePost> posts = postDao.selectList(wrapper);


        //复用postlists方法查询模糊匹配到的发帖人信息，查当前用户是否收藏点赞，然后组装vo返回
        List<Long> userIds = posts.stream()
                .map(CoffeePost::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = userDao.selectBatchIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

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


    public Boolean toggleLike(Long postId) {
        // 1. 拿当前登录用户ID
        Long userId = getCurrentUserId();
        if (userId == null){
            throw new RuntimeException("请先登录");
        }
        // 2. 查有没有点过赞
        // SQL: SELECT COUNT(*) FROM coffee_like WHERE post_id = ? AND user_id = ?
        Long count = likeDao.selectCount(new LambdaQueryWrapper<CoffeeLike>()
                .eq(CoffeeLike::getPostId, postId)
                .eq(CoffeeLike::getUserId, userId)
        );

        if (count > 0) {
            //3.点过了 取消点赞
            // 取消：SQL: DELETE FROM coffee_like WHERE post_id = ? AND user_id = ?
            likeDao.delete(
                    new LambdaQueryWrapper<CoffeeLike>()
                            .eq(CoffeeLike::getPostId,postId)
                            .eq(CoffeeLike::getUserId,userId)
            );
            //like_count - 1(但不能小于0)
            // SQL: UPDATE coffee_post SET like_count = ? WHERE id = ?
            CoffeePost post = postDao.selectById(postId);
            post.setLikeCount(Math.max(0,post.getLikeCount()) - 1);
            postDao.updateById(post);
            return false;
        }else {
            //4.没点过 -> 点赞
            // 点赞：SQL: INSERT INTO coffee_like (post_id, user_id) VALUES (?, ?)
            CoffeeLike like = new CoffeeLike();
            like.setPostId(postId);
            like.setUserId(userId);
            likeDao.insert(like);
            //like_count + 1
            // SQL: UPDATE coffee_post SET like_count = ? WHERE id = ?
            CoffeePost post = postDao.selectById(postId);
            post.setLikeCount(post.getLikeCount() + 1);
            postDao.updateById(post);
            return true;
        }
    }

    public Boolean toggleFavorite(Long postId) {
        Long userId = getCurrentUserId();
        if (userId == null){
            throw new RuntimeException("请先登录");
        }
        //第一步：查有没有收藏过 收藏/取消收藏（toggle）
        //SELECT COUNT(*) FROM coffee_favorite WHERE post_id = 1 AND user_id = 5
        Long count = favoriteDao.selectCount(new LambdaQueryWrapper<CoffeeFavorite>()
                .eq(CoffeeFavorite::getPostId, postId)
                .eq(CoffeeFavorite::getUserId, userId)
        );
        if (count == 0){
            //用户未收藏 将收藏 return true
            //INSERT INTO coffee_favorite (post_id, user_id) VALUES (1, 5)
            CoffeeFavorite favorite = new CoffeeFavorite();
            favorite.setPostId(postId);
            favorite.setUserId(userId);
            favoriteDao.insert(favorite);
            //UPDATE coffee_post SET favorite_count = favorite_count + 1 WHERE id = 1
            CoffeePost post = postDao.selectById(postId);
            post.setFavoriteCount(post.getFavoriteCount() + 1);
            postDao.updateById(post);
            return true;
        }else {
            //用户已收藏 将不收藏 return false
            //DELETE FROM coffee_favorite WHERE post_id = 1 AND user_id = 5
            favoriteDao.delete(new LambdaQueryWrapper<CoffeeFavorite>()
                    .eq(CoffeeFavorite::getPostId,postId)
                    .eq(CoffeeFavorite::getUserId,userId)
            );
            //UPDATE coffee_post SET favorite_count = favorite_count - 1 WHERE id = 1
            CoffeePost post = postDao.selectById(postId);
            post.setFavoriteCount(Math.max(0,post.getFavoriteCount() - 1));
            postDao.updateById(post);
            return false;
        }


    }

    public CommentVO addComment(Long postId, CommentCreateFrom from) {
        // 1. 拿当前登录用户
        Long userId = getCurrentUserId();
        if (userId == null){
            throw new RuntimeException("请先登录");
        }
        // 2. 查帖子存不存在
        // SQL: SELECT * FROM coffee_post WHERE id = ?
        CoffeePost post = postDao.selectById(postId);
        if (post == null || post.getStatus() == 0){
            throw new RuntimeException("帖子不存在或者已删除");
        }
        // 3. 插入评论
        // SQL: INSERT INTO coffee_comment (post_id, user_id, content) VALUES (?, ?, ?)
        CoffeeComment comment = new CoffeeComment();
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setContent(from.getContent());
        commentDao.insert(comment);

        // 4. 评论数+1
        // SQL: UPDATE coffee_post SET comment_count = comment_count + 1 WHERE id = ?
        post.setCommentCount(post.getCommentCount() + 1);
        postDao.updateById(post);

        // 5. 查自己的昵称，组装 CommentVO 返回给前端
        // SQL: SELECT * FROM sys_user WHERE id = ?
        User user = userDao.selectById(userId);
        return CommentVO.builder()
                .id(comment.getId())
                .userId(userId)
                .username(user != null ? user.getUsername() : "未知")
                .content(from.getContent())
                .createTime(comment.getCreateTime())
                .build();
        //POST /api/coffee/posts/1/comment
        //Body: {"content":"这杯咖啡看起来真不错"}
        //  │
        //  ▼
        //JwtAuthenticationFilter → userId=5
        //  │
        //  ▼
        //CoffeeService.addComment(1, form)
        //  ├─ SELECT * FROM coffee_post WHERE id = 1              → 校验帖子存在
        //  ├─ INSERT INTO coffee_comment (post_id, user_id, content) VALUES (1, 5, '真不错')
        //  ├─ UPDATE coffee_post SET comment_count = 6 WHERE id = 1
        //  └─ SELECT * FROM sys_user WHERE id = 5                  → 拿昵称
        //  │
        //  ▼
        //返回 {"code":200, "data": {"id":10, "username":"lsk", "content":"真不错", "createTime":"..."}}


    }




    //核心思想：任何修改/删除操作都要校验 userId == 创建者，防止 A 用户删 B 用户的帖子。
    public void deletePost(Long id) {

        // 1. 拿当前登录用户
        Long userId = getCurrentUserId();
        if (userId == null){
            throw new RuntimeException("请先登录");
        }
        // SQL: SELECT * FROM coffee_post WHERE id = ?
        CoffeePost post = postDao.selectById(id);
        if (post == null){throw new RuntimeException("帖子不存在");}
        if (!post.getUserId().equals(userId)){throw new RuntimeException("这不是你的帖子qaq");}
        // 软删除：改状态为0，不真删数据
        // SQL: UPDATE coffee_post SET status = 0 WHERE id = ?
        post.setStatus(0);
        postDao.updateById(post);//yihuo
    }

    public void updatePost(Long id, PostCreateFrom from) {
        Long userId = getCurrentUserId();
        if (userId == null){throw  new RuntimeException("请先登录");}

        // SQL: SELECT * FROM coffee_post WHERE id = ?
        CoffeePost post = postDao.selectById(id);
        if (post == null) throw new RuntimeException("帖子不存在");
        if (!post.getUserId().equals(userId)) throw new RuntimeException("这不是你发布的帖子哦 不能修改qaq");

        // 覆盖字段
        post.setTitle(from.getTitle());
        post.setContent(from.getContent());
        post.setCoffeeName(from.getCoffeeName());
        post.setCoffeeBrand(from.getCoffeeBrand());
        post.setLocation(from.getLocation());
        post.setPostType(from.getPostType());
        if (from.getImages() != null && !from.getImages().isEmpty()){
            post.setImages(JSONUtil.toJsonStr(from.getImages()));
        }

        // SQL: UPDATE coffee_post SET title=?, content=?, ... WHERE id = ?
        postDao.updateById(post);

    }


    public void deleteComment(Long commentId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        // SQL: SELECT * FROM coffee_comment WHERE id = ?
        CoffeeComment comment = commentDao.selectById(commentId);
        if (comment == null) throw new RuntimeException("评论不存在");

        // SQL: SELECT * FROM coffee_post WHERE id = ?
        CoffeePost post = postDao.selectById(comment.getPostId());

        // 权限：评论作者 OR 帖子作者 都能删
        boolean isCommentAuthor = comment.getUserId().equals(userId);
        boolean isPostAuthor = post.getUserId().equals(userId);
        if (!isCommentAuthor && !isPostAuthor) {
            throw new RuntimeException("无权删除该评论");
        }

        // SQL: DELETE FROM coffee_comment WHERE id = ?
        commentDao.deleteById(commentId);

        // SQL: UPDATE coffee_post SET comment_count = comment_count - 1 WHERE id = ?
        post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
        postDao.updateById(post);

        //删除评论 → 你是谁？
        //  ├─ 评论作者 → 能删 ✅
        //  ├─ 帖子作者 → 能删 ✅  （管理自己帖子下的评论）
        //  └─ 路人     → 不能删 ❌
    }

}
