package cn.lx.worldcoffee.module.coffee.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.module.coffee.dao.*;
import cn.lx.worldcoffee.module.coffee.domain.*;
import cn.lx.worldcoffee.module.coffee.domain.from.CommentCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.from.PostCreateFrom;
import cn.lx.worldcoffee.module.coffee.domain.from.ReportCreatFrom;
import cn.lx.worldcoffee.module.coffee.domain.vo.CommentVO;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostDetailVO;
import cn.lx.worldcoffee.module.coffee.domain.vo.PostListVO;
import cn.lx.worldcoffee.module.notification.domain.NotificationEvent;
import cn.lx.worldcoffee.module.notification.service.NotificationService;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.coffee.domain.CoffeeLike;
import org.springframework.web.multipart.MultipartFile;


import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
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
    private final CoffeeCommentLikeDao commentLikeDao;
    private final UserFollowDao followDao;
    private final NotificationService notificationService;
    private final PostReportDao postReportDao;

    @Value("${upload.path}")
    private String uploadPath;



    // ===== =======   工具方法 =======   =====
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

    /**
     * 把帖子列表增强为 PostListVO 列表
     * 统一处理：批量查用户名、点赞状态、收藏状态
     */
    //方法采用了"先批量查、再逐条拼"的策略，避免了在循环里逐条查数据库的 N+1 问题。
    // 整个流程分三步：查用户、查当前用户交互状态、组装 VO。
    // 这是一个比较成熟的写法，说明作者对性能有一定意识。嘻嘻
    /** 关于解决n+1问题
     *  没解决（N+1）：                         解决后：
     * ┌────────┐  ┌────────┐                 ┌────────┐
     * │ 查帖子  │  │ 查帖子  │                 │ 查帖子  │
     * │ (1次)  │  │ (1次)  │                 │ (1次)  │
     * └───┬────┘  └───┬────┘                 └───┬────┘
     *     │           │                          │
     *     ├ 查用户 id=3  │                 ┌─────┴─────┐
     *     ├ 查用户 id=3  │  重复          │ 查用户 IN(1,3,5) │
     *     ├ 查用户 id=5  │  浪费          │ (1次)     │
     *     ├ 查用户 id=1  │                 └───────────┘
     *     ...          │
     *   10次           │
     *
     *
     * 没解决n+1：
     * for (CoffeePost post : posts) {
     *     User user = userDao.selectById(post.getUserId());   // 循环里查库
     *     // 组装VO...
     * }
     * 第1条：SELECT * FROM coffee_post WHERE status = 1     ← 1条（查帖子）
     * 第2条：SELECT * FROM sys_user WHERE id = 3            ← 帖子1的作者
     * 第3条：SELECT * FROM sys_user WHERE id = 3            ← 帖子2的作者（同一个人的又查了一次）
     * 第4条：SELECT * FROM sys_user WHERE id = 5            ← 帖子3的作者
     * 第5条：SELECT * FROM sys_user WHERE id = 1            ← 帖子4的作者
     * ...
     * 解决以后：
     * // 1次查出所有帖子（1条SQL）
     * List<CoffeePost> posts = postDao.selectList(wrapper);
     * // 拿出所有 userId，去重
     * List<Long> userIds = posts.stream().map(CoffeePost::getUserId).distinct().collect(...);
     * // 一次性查出所有用户（1条SQL）
     * Map<Long, User> userMap = userDao.selectBatchIds(userIds);
     *
     * 第1条：SELECT * FROM coffee_post WHERE status = 1     ← 1条
     * 第2条：SELECT * FROM sys_user WHERE id IN (1,3,5)     ← 1条（三个用户一次查完）
     *
     * 为什么这叫 N+1 问题
     *        查帖子	     查用户	          总计
     * 没解决	1次	     N次（每帖一次）	  N+1
     * 解决	    1次	     1次（批量）	      2
     * 解决的关键：把"循环里每条查一次"改成"循环前一次性全查出来，用 IN 条件"。
     *
     */
    public List<PostListVO> buildPostListVO(List<CoffeePost> posts) {
        if (posts.isEmpty()) return List.of();

        /**1.为什么在selectBatchIds之后还要进行一个流处理？
         * 因为 selectBatchIds 返回的是一个 List<User>，不是 Map。
         * selectBatchIds 返回的是：
         * List<User> users = [
         *     User{id=3, username="张三"},
         *     User{id=5, username="李四"}
         * ]
         * 之后你要在循环里通过 userId 快速找到对应的 username。如果用 List：
         * // 每次都要遍历 List 找，O(n) 复杂度
         * for (User u : users) {
         *     if (u.getId().equals(post.getUserId())) return u.getUsername();
         * }
         * // get(key) 直接找到，O(1) 复杂度
         * Map<Long, User> userMap = {
         *     3: User{张三},
         *     5: User{李四}
         * }
         * userMap.get(post.getUserId())  // 一步到位，不用循环
         * 第1个 stream：posts → 提取userId → 去重 → [3,5]  ← 准备批量查询的参数
         *
         * selectBatchIds([3,5]) → List<User>  ← 查数据库，返回的是 List
         *
         * 第2个 stream：List<User> → 转成 Map<Long,User>  ← 把 List 变 Map，方便后面 get 查找
         *
         * 关于这种lamda表达式
         * // 简写（Lambda + 方法引用）
         * users.stream().collect(Collectors.toMap(User::getId, u -> u));
         *
         * // 等价于完整写法
         * users.stream().collect(Collectors.toMap(
         *     new Function<User, Long>() {
         *         @Override
         *         public Long apply(User user) {
         *             return user.getId();    // key = 用户ID
         *         }
         *     },
         *     new Function<User, User>() {
         *         @Override
         *         public User apply(User user) {
         *             return user;            // value = 用户自己
         *         }
         *     }
         * ));
         * 本质:user::getId(方法引用简写) == user -> user.getId(lambda表达式写法)
         * 本质:u -> u    直接返回元素本身   // 输入是什么，输出就是什么
         * :: 即前调用后  u->u 它本身
         *
         * 这里也可以引出stream = 让集合在流水线上被逐批加工的流程
         * List<User> users = [User{id=3, "张三"}, User{id=5, "李四"}]
         *        │
         *        ▼
         * .stream()
         *        │  流水线启动
         *        ▼
         * .collect(Collectors.toMap(
         *        │
         *        ├─ User::getId     ← 第1个user：取 id=3 作为 Map 的 key
         *        │                      第2个user：取 id=5 作为 Map 的 key
         *        │
         *        └─ u -> u          ← 第1个user：value 就是 User{张三}
         *                              第2个user：value 就是 User{李四}
         * ))
         *        │
         *        ▼
         * Map<Long, User> = {3: User{张三}, 5: User{李四}}
         */
        // 1. 批量查发帖人
        // SQL: SELECT * FROM sys_user WHERE id IN (?)
        Map<Long, User> userMap = userDao.selectBatchIds(
                posts.stream().map(CoffeePost::getUserId).distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        // 2. 查当前用户点赞/收藏状态
        Long currentUserId = getCurrentUserId();
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> favoritedPostIds = new HashSet<>();
        if (currentUserId != null) {
            List<Long> postIds = posts.stream().map(CoffeePost::getId).collect(Collectors.toList());
            // SQL: SELECT * FROM coffee_like WHERE user_id = ? AND post_id IN (?)
            likedPostIds = likeDao.selectList(new LambdaQueryWrapper<CoffeeLike>()
                    .eq(CoffeeLike::getUserId, currentUserId)
                    .in(CoffeeLike::getPostId, postIds)
            ).stream().map(CoffeeLike::getPostId).collect(Collectors.toSet());
            // SQL: SELECT * FROM coffee_favorite WHERE user_id = ? AND post_id IN (?)
            favoritedPostIds = favoriteDao.selectList(new LambdaQueryWrapper<CoffeeFavorite>()
                    .eq(CoffeeFavorite::getUserId, currentUserId)
                    .in(CoffeeFavorite::getPostId, postIds)
            ).stream().map(CoffeeFavorite::getPostId).collect(Collectors.toSet());
        }

        // 3. 组装 VO
        //posts         ← 帖子列表（有 id、title、content...）
        //userMap       ← 用户查找表（{3→张三, 5→李四}）
        //finalLiked    ← 我点过赞的帖子ID集合（{1, 3}）
        //finalFavorited ← 我收藏的帖子ID集合（{3}）

        //这是 Java 的限制：Lambda 表达式里只能引用 final 或 "实际上不可变" 的变量。
        // 如果不赋值给一个 final 变量，
        // 编译器不让你在 .map(post -> { ... }) 里面用。没有别的意思，就是换个名字让编译器闭嘴。
        final Set<Long> finalLiked = likedPostIds;
        final Set<Long> finalFavorited = favoritedPostIds;
        return posts.stream().map(post -> {     // 遍历每条帖子，把 CoffeePost → PostListVO
            User user = userMap.get(post.getUserId());   // 根据userId从Map里取用户
            return PostListVO.builder()
                    // ---- 从帖子本身拿 ----
                    .id(post.getId())
                    .userId(post.getUserId())
                    .title(post.getTitle())
                    .content(truncate(post.getContent(), 80))
                    .images(parseImages(post.getImages()))
                    .coffeeName(post.getCoffeeName())
                    .coffeeBrand(post.getCoffeeBrand())
                    .location(post.getLocation())
//                    .postType(post.getPostType())
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .favoriteCount(post.getFavoriteCount())
                    // ---- 从用户Map查 ----
                    .username(user != null ? user.getUsername() : "未知用户")
                    .avatar(user != null ? user.getAvatar() : null)
                    // ---- 从点赞/收藏集合判断 ----
                    .likedByMe(finalLiked.contains(post.getId()))// 当前帖子的ID在不在我的点赞列表里
                    .favoritedByMe(finalFavorited.contains(post.getId()))// 在不在我的收藏列表里
                    .createTime(post.getCreateTime())
                    .build();
        }).collect(Collectors.toList()); // 所有组装好的VO收集成List

    }

    // ========   业务方法    ===========
    public List<PostListVO> listPosts(Integer page, Integer size) {
        // ===== 1. 分页查帖子 =====
        // SQL: SELECT * FROM coffee_post WHERE status = 1 ORDER BY create_time DESC LIMIT 0,10
        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeePost::getStatus,1)     //只查正常帖子
                .orderByDesc(CoffeePost::getCreateTime)  //最新在前
                .last("LIMIT " + (page - 1) * size + "," + size);//分页
        List<CoffeePost> posts = postDao.selectList(wrapper);
        return buildPostListVO(posts);
    }

    public void createPost(PostCreateFrom from) {
        // 1. 从 SecurityContext 拿当前登录用户ID
        Long userId = getCurrentUserId();
        if (userId == null){
            throw new ServiceException("请先登录");
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
//        post.setPostType(from.getPostType());
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
            throw new ServiceException("帖子不存在或者已删除");
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

        //这是边界判断的基础功。。selectBatchIds 传空列表 MyBatis-Plus 不会自动跳过，
        // 直接拼出 IN () 炸 SQL。
        //帖子 1-9 有评论 → commentIds = [2, 5, 7] →WHERE id IN (2,5,7)
        // ✅ 帖子 10+ 没评论 → commentIds = [] → WHERE id IN ( ) 💥
        // 4. 批量查评论人信息
        // SQL: SELECT * FROM sys_user WHERE id IN (2,5,7,...)   ← 批量查评论人
        List<Long> commentIds = commentList.stream()
                .map(CoffeeComment::getUserId)
                .distinct()
                .collect(Collectors.toList());

//        Map<Long, User> userMap = Collections.emptyMap();
//        if (!commentIds.isEmpty()){
//            userMap = userDao.selectBatchIds(commentIds)
//                    .stream()
//                    .collect(Collectors.toMap(User::getId,u -> u));
//        }
        Map<Long, User> userMap = commentIds.isEmpty()
                ? Collections.emptyMap()
                : userDao.selectBatchIds(commentIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));
//为什么：Java 的 Lambda 里引用外部变量，
// 要求这个变量是 final 或者"实际只赋值过一次"。
// 三目运算符在声明时就确定值，只赋值一次，编译通过。
        //5.组装评论
        List<CommentVO> commentVOs = commentList.stream().map(c -> {
            User u = userMap.get(c.getUserId());
            return CommentVO.builder()
                    .id(c.getId())
                    .userId(c.getUserId())
                    .username(u != null ? u.getUsername() : "未知")
                    .avatar(u != null ? u.getAvatar() : null)
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
                .avatar(author != null ? author.getAvatar() : null)
                .title(post.getTitle())
                .content(post.getContent())                      // 完整内容
                .images(parseImages(post.getImages()))            // 复用已有方法
                .coffeeName(post.getCoffeeName())
                .coffeeBrand(post.getCoffeeBrand())
                .location(post.getLocation())
//                .postType(post.getPostType())
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
        return buildPostListVO(postDao.selectList(wrapper));
    }

    public Boolean toggleLike(Long postId) {
        // 1. 拿当前登录用户ID
        Long userId = getCurrentUserId();
        if (userId == null){
            throw new ServiceException("请先登录");
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
            // 发通知（必须在 return 之前，否则代码不可达）
            if (!post.getUserId().equals(userId)) {
                notificationService.send(NotificationEvent.builder()
                        .receiverId(post.getUserId())
                        .senderId(userId)
                        .type("LIKE")
                        .postId(postId)
                        .content("赞了你的帖子")
                        .build());
            }
            return true;
        }
    }

    public Boolean toggleFavorite(Long postId) {
        Long userId = getCurrentUserId();
        if (userId == null){
            throw new ServiceException("请先登录");
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
            if (!post.getUserId().equals(userId)) {
                notificationService.send(NotificationEvent.builder()
                        .receiverId(post.getUserId())
                        .senderId(userId)
                        .type("FAVORITE")
                        .postId(postId)
                        .content("收藏了你的帖子")
                        .build());
            }
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
            throw new ServiceException("请先登录");
        }
        // 2. 查帖子存不存在
        // SQL: SELECT * FROM coffee_post WHERE id = ?
        CoffeePost post = postDao.selectById(postId);
        if (post == null || post.getStatus() == 0){
            throw new ServiceException("帖子不存在或者已删除");
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

        // 6. 发评论通知给帖子作者（自己评论自己不通知）
        if (!post.getUserId().equals(userId)) {
            notificationService.send(NotificationEvent.builder()
                    .receiverId(post.getUserId())
                    .senderId(userId)
                    .type("COMMENT")
                    .postId(postId)
                    .commentId(comment.getId())
                    .content(truncate(from.getContent(), 30))
                    .build());
        }

        return CommentVO.builder()
                .id(comment.getId())
                .userId(userId)
                .username(user != null ? user.getUsername() : "未知")
                .avatar(user != null ? user.getAvatar() : null)
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
            throw new ServiceException("请先登录");
        }
        // SQL: SELECT * FROM coffee_post WHERE id = ?
        CoffeePost post = postDao.selectById(id);
        if (post == null){throw new ServiceException("帖子不存在");}
        if (!post.getUserId().equals(userId)){throw new ServiceException("这不是你的帖子qaq");}
        // 软删除：改状态为0，不真删数据
        // SQL: UPDATE coffee_post SET status = 0 WHERE id = ?
        post.setStatus(0);
        postDao.updateById(post);//yihuo
    }

    public void updatePost(Long id, PostCreateFrom from) {
        Long userId = getCurrentUserId();
        if (userId == null){throw  new ServiceException("请先登录");}

        // SQL: SELECT * FROM coffee_post WHERE id = ?
        CoffeePost post = postDao.selectById(id);
        if (post == null) throw new ServiceException("帖子不存在");
        if (!post.getUserId().equals(userId)) throw new ServiceException("这不是你发布的帖子哦 不能修改qaq");

        // 覆盖字段
        post.setTitle(from.getTitle());
        post.setContent(from.getContent());
        post.setCoffeeName(from.getCoffeeName());
        post.setCoffeeBrand(from.getCoffeeBrand());
        post.setLocation(from.getLocation());
//        post.setPostType(from.getPostType());
        if (from.getImages() != null && !from.getImages().isEmpty()){
            post.setImages(JSONUtil.toJsonStr(from.getImages()));
        }

        // SQL: UPDATE coffee_post SET title=?, content=?, ... WHERE id = ?
        postDao.updateById(post);

    }

    public void deleteComment(Long commentId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // SQL: SELECT * FROM coffee_comment WHERE id = ?
        CoffeeComment comment = commentDao.selectById(commentId);
        if (comment == null) throw new ServiceException("评论不存在");

        // SQL: SELECT * FROM coffee_post WHERE id = ?
        CoffeePost post = postDao.selectById(comment.getPostId());

        // 权限：评论作者 OR 帖子作者 都能删
        boolean isCommentAuthor = comment.getUserId().equals(userId);
        boolean isPostAuthor = post.getUserId().equals(userId);
        if (!isCommentAuthor && !isPostAuthor) {
            throw new ServiceException("无权删除该评论");
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

    public List<PostListVO> getMyPosts(Integer page, Integer size) {
        //和 listPosts 的区别只有这一个地方：.eq(CoffeePost::getUserId, userId) 把查所有人的帖改成只查当前登录用户的帖，其余一模一样。

        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");
        // SQL: SELECT * FROM coffee_post WHERE user_id = ? AND status = 1
        //      ORDER BY create_time DESC LIMIT ?,?
        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeePost::getUserId,userId)    // ← 只查自己的
                .eq(CoffeePost::getStatus,1)
                .orderByDesc(CoffeePost::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size);
        return buildPostListVO(postDao.selectList(wrapper));
    }

    //收藏和点赞不能缩到更短的原因：它们要先查关联表拿 ID 列表再手动分页，
    // 这和直接查 coffee_post 的 LIMIT 分页不同。
    // 但拿到帖子列表后直接扔给 buildPostListVO 就完事了，后半段全部复用。
    public List<PostListVO> getMyFavorites(Integer page, Integer size) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // 第1步：查收藏记录，拿到帖子ID列表
        // SQL: SELECT post_id FROM coffee_favorite WHERE user_id = ? ORDER BY create_time DESC
        List<Long> allPostIds = favoriteDao.selectList(
                new LambdaQueryWrapper<CoffeeFavorite>()
                        .eq(CoffeeFavorite::getUserId, userId)
                        .orderByDesc(CoffeeFavorite::getCreateTime)
        ).stream().map(CoffeeFavorite::getPostId).collect(Collectors.toList());
        if (allPostIds.isEmpty()) return List.of();

        // 第2步：手动分页
        int start = (page - 1) * size;
        if (start >= allPostIds.size()) return List.of();
        int end = Math.min(start + size, allPostIds.size());
        List<Long> postIds = allPostIds.subList(start, end);

        // 第3步：按ID列表查帖子
        // SQL: SELECT * FROM coffee_post WHERE id IN (?,?,?) AND status = 1
        List<CoffeePost> posts = postDao.selectList(
                new LambdaQueryWrapper<CoffeePost>()
                        .in(CoffeePost::getId, postIds)
                        .eq(CoffeePost::getStatus, 1)
                        .orderByDesc(CoffeePost::getCreateTime)
        );

        // 第4步：复用公共方法，组装VO
        return buildPostListVO(posts);
    }

    public List<PostListVO> getMyLikes(Integer page, Integer size) {
        //收藏和点赞列表唯一区别：查收藏查 coffee_favorite 表，
        //点赞查 coffee_like 表，其余批量查帖子、批量查用户、组装VO全都一样。
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // SQL: SELECT post_id FROM coffee_like WHERE user_id = ? ORDER BY create_time DESC
        List<Long> allPostIds = likeDao.selectList(
                new LambdaQueryWrapper<CoffeeLike>()
                        .eq(CoffeeLike::getUserId, userId)
                        .orderByDesc(CoffeeLike::getCreateTime)
        ).stream().map(CoffeeLike::getPostId).collect(Collectors.toList());

        if (allPostIds.isEmpty()) return List.of();
        // 第2步：手动分页
        int start = (page - 1) * size;
        if (start >= allPostIds.size()) return List.of();
        int end = Math.min(start + size, allPostIds.size());
        List<Long> postIds = allPostIds.subList(start, end);

        // 第3步：按ID列表查帖子
        // SQL: SELECT * FROM coffee_post WHERE id IN (?,?,?) AND status = 1
        List<CoffeePost> posts = postDao.selectList(
                new LambdaQueryWrapper<CoffeePost>()
                        .in(CoffeePost::getId, postIds)
                        .eq(CoffeePost::getStatus, 1)
                        .orderByDesc(CoffeePost::getCreateTime)
        );

        // 第4步：复用公共方法，组装VO
        return buildPostListVO(posts);
    }

    public String uploadImage(MultipartFile file) {
        /**
         * 上传图片
         * 返回：http://localhost:8080/uploads/xxx.png
         */
        if (file.isEmpty()) throw new ServiceException("文件不能为空");
        // 校验文件类型：只允许图片
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ServiceException("只能上传图片");
        }

        // 生成唯一文件名：时间戳_原文件名
        String originalName = file.getOriginalFilename();
        String suffix = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : ".png";
        String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + suffix;

        // 保存到 uploads 目录
        // uploads 目录通过 WebMvcConfig 已映射到 /uploads/** 可浏览器访问
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (!created) throw new ServiceException("无法创建上传目录");
        }

        try {
            file.transferTo(new File(uploadDir, fileName));
        } catch (IOException e) {
            // 把原始错误带上，方便排查
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }
        return "http://localhost:8080/uploads/" + fileName;


        //const formData = new FormData()
        //formData.append('file', fileInput.files[0])
        //
        //const { data } = await api.post('/coffee/upload', formData, {
        //  headers: { 'Content-Type': 'multipart/form-data' }
        //})
        //
        //// data.data = "http://localhost:8080/uploads/1719843200_a1b2c3d4.jpg"
        //

        //   数据流
        //前端选择图片
        //  │
        //  ▼
        //POST /api/coffee/upload (FormData)
        //  │
        //  ▼
        //CoffeeService.uploadImage(file)
        //  ├─ 校验：是不是图片？
        //  ├─ 生成唯一文件名：时间戳_UUID.png
        //  ├─ 保存到 uploads/ 目录
        //  └─ 返回 http://localhost:8080/uploads/xxx.png
        //  │
        //  ▼
        //前端拿到URL → 填到 images 字段 → 随发帖请求一起提交
    }

    public List<PostListVO> getHotPosts(Integer page, Integer size) {
        //用lamdaquerywrapper的话
        //orderByDesc(SFunction<T, ?> column) 要的是一个 Lambda 方法引用，
        // 编译器把 CoffeePost::getLikeCount 映射成 like_count 字段名。
        // 传字符串 "(like_count+...)" 匹配不上 SFunction 类型，所以爆红。
        // 需要用原始SQL的话，直接用 .last() 追加。
        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeePost::getStatus, 1)
                .last("ORDER BY (like_count + comment_count + favorite_count) DESC, create_time DESC LIMIT "
                        + (page - 1) * size + "," + size);

        return buildPostListVO(postDao.selectList(wrapper));
    }

    public Boolean toggleCommentLike(Long commentId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");


        // SQL: SELECT COUNT(*) FROM coffee_comment_like
        // WHERE comment_id = ? AND user_id = ?
        LambdaQueryWrapper<CoffeeCommentLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeeCommentLike::getCommentId, commentId)
                .eq(CoffeeCommentLike::getUserId, userId);
        Long count = commentLikeDao.selectCount(wrapper);

        if (count > 0) {
            // 取消点赞
            // SQL: DELETE FROM coffee_comment_like WHERE comment_id = ? AND user_id = ?
            commentLikeDao.delete(wrapper);
            // SQL: UPDATE coffee_comment SET like_count = like_count - 1 WHERE id = ?
            CoffeeComment comment = commentDao.selectById(commentId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            commentDao.updateById(comment);
            return false;
        }else {
            // 点赞
            // SQL: INSERT INTO coffee_comment_like (comment_id, user_id) VALUES (?, ?)
            CoffeeCommentLike like = new CoffeeCommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            commentLikeDao.insert(like);
            // SQL: UPDATE coffee_comment SET like_count = like_count + 1 WHERE id = ?
            CoffeeComment comment = commentDao.selectById(commentId);
            comment.setLikeCount(comment.getLikeCount() + 1);
            commentDao.updateById(comment);
            if (!comment.getUserId().equals(userId)) {
                notificationService.send(NotificationEvent.builder()
                        .receiverId(comment.getUserId())
                        .senderId(userId)
                        .type("LIKE")
                        .postId(comment.getPostId())
                        .commentId(commentId)
                        .content("点赞了你的评论")
                        .build());
            }
            return true;
        }
    }

    public List<PostListVO> getFollowingPosts(Integer page, Integer size) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // SQL: SELECT followee_id FROM user_follow WHERE follower_id = ?
        List<Long> followeeIds = followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
        ).stream().map(UserFollow::getFolloweeId).collect(Collectors.toList());

        if (followeeIds.isEmpty()) return List.of();

        // SQL: SELECT * FROM coffee_post
        // WHERE user_id IN (?,?,?) AND status = 1 ORDER BY create_time DESC LIMIT ?,?
        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CoffeePost::getUserId,followeeIds)
                .eq(CoffeePost::getStatus,1)
                .orderByDesc(CoffeePost::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," +size);
        return buildPostListVO(postDao.selectList(wrapper));
    }

    /**
     * 设计要点：
     *
     * 同一人同一帖只允许举报一次，防止刷举报
     * 不能举报自己的帖子
     * status 字段留给管理后台处理，目前默认 0
     */
    public void reportPost(Long postId, ReportCreatFrom from) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        // 校验帖子存在
        CoffeePost post = postDao.selectById(postId);
        if (post == null || post.getStatus() == 0) throw new ServiceException("帖子不存在");

        // 不能举报自己的帖子
        if (post.getUserId().equals(userId)) throw new ServiceException("不能举报自己的帖子");

        // 查是否已经举报过（同一人同一帖只允许举报一次）
        Long count = postReportDao.selectCount(new LambdaQueryWrapper<PostReport>()
                .eq(PostReport::getPostId, postId)
                .eq(PostReport::getReporterId, userId));
        if (count > 0) throw new ServiceException("你已经举报过该帖子");

        PostReport report = new PostReport();
        report.setPostId(postId);
        report.setReporterId(userId);
        report.setReason(from.getReason());
        report.setStatus(0);
        report.setCreateTime(LocalDateTime.now());

        // SQL: INSERT INTO post_report (post_id, reporter_id, reason, status) VALUES (?, ?, ?, 0)
        postReportDao.insert(report);

    }
}
