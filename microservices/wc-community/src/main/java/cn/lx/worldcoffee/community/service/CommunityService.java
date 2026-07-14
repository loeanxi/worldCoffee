package cn.lx.worldcoffee.community.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.community.dao.*;
import cn.lx.worldcoffee.community.domain.*;
import cn.lx.worldcoffee.community.domain.from.CommentCreateFrom;
import cn.lx.worldcoffee.community.domain.from.PostCreateFrom;
import cn.lx.worldcoffee.community.domain.from.ReportCreatFrom;
import cn.lx.worldcoffee.community.domain.vo.CommentVO;
import cn.lx.worldcoffee.community.domain.vo.PostDetailVO;
import cn.lx.worldcoffee.community.domain.vo.PostListVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CoffeePostDao postDao;
    private final CoffeeLikeDao likeDao;
    private final CoffeeFavoriteDao favoriteDao;
    private final CoffeeCommentDao commentDao;
    private final CoffeeCommentLikeDao commentLikeDao;
    private final UserFollowDao followDao;
    private final PostReportDao postReportDao;

    @Value("${file.upload.path:D:\\mycode\\worldCoffee\\worldCoffee\\uploads\\}")
    private String uploadPath;

    // ==================== 工具方法 ====================

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return List.of();
        try {
            return JSONUtil.toList(imagesJson, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 把帖子列表增强为 PostListVO 列表
     * 批量查用户名、点赞状态、收藏状态，避免 N+1 问题
     */
    public List<PostListVO> buildPostListVO(List<CoffeePost> posts) {
        if (posts.isEmpty()) return List.of();

        // TODO: 通过 OpenFeign 调用 wc-user 获取用户信息
        // 暂时用占位数据
        Map<Long, String> usernameMap = new HashMap<>();
        Map<Long, String> avatarMap = new HashMap<>();

        // 2. 查当前用户点赞/收藏状态
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> favoritedPostIds = new HashSet<>();
        if (currentUserId != null) {
            List<Long> postIds = posts.stream().map(CoffeePost::getId).collect(Collectors.toList());
            likedPostIds = likeDao.selectList(new LambdaQueryWrapper<CoffeeLike>()
                    .eq(CoffeeLike::getUserId, currentUserId)
                    .in(CoffeeLike::getPostId, postIds)
            ).stream().map(CoffeeLike::getPostId).collect(Collectors.toSet());

            favoritedPostIds = favoriteDao.selectList(new LambdaQueryWrapper<CoffeeFavorite>()
                    .eq(CoffeeFavorite::getUserId, currentUserId)
                    .in(CoffeeFavorite::getPostId, postIds)
            ).stream().map(CoffeeFavorite::getPostId).collect(Collectors.toSet());
        }

        final Set<Long> finalLiked = likedPostIds;
        final Set<Long> finalFavorited = favoritedPostIds;

        return posts.stream().map(post -> {
            return PostListVO.builder()
                    .id(post.getId())
                    .userId(post.getUserId())
                    .title(post.getTitle())
                    .content(truncate(post.getContent(), 80))
                    .images(parseImages(post.getImages()))
                    .coffeeName(post.getCoffeeName())
                    .coffeeBrand(post.getCoffeeBrand())
                    .location(post.getLocation())
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .favoriteCount(post.getFavoriteCount())
                    .username(usernameMap.getOrDefault(post.getUserId(), "未知用户"))
                    .avatar(avatarMap.get(post.getUserId()))
                    .likedByMe(finalLiked.contains(post.getId()))
                    .favoritedByMe(finalFavorited.contains(post.getId()))
                    .createTime(post.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    // ==================== 业务方法 ====================

    public List<PostListVO> listPosts(Integer page, Integer size, String sort) {
        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeePost::getStatus, 1);

        if ("random".equals(sort)) {
            wrapper.last("ORDER BY RAND() LIMIT " + (page - 1) * size + "," + size);
        } else {
            wrapper.orderByDesc(CoffeePost::getCreateTime)
                    .orderByDesc(CoffeePost::getId)
                    .last("LIMIT " + (page - 1) * size + "," + size);
        }

        List<CoffeePost> posts = postDao.selectList(wrapper);
        return buildPostListVO(posts);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createPost(PostCreateFrom from) {
        Long userId = SecurityUtils.requireUserId();

        CoffeePost post = new CoffeePost();
        post.setUserId(userId);
        post.setTitle(from.getTitle());
        post.setContent(from.getContent());

        if (from.getImages() != null && !from.getImages().isEmpty()) {
            post.setImages(JSONUtil.toJsonStr(from.getImages()));
        }
        post.setCoffeeName(from.getCoffeeName());
        post.setCoffeeBrand(from.getCoffeeBrand());
        post.setLocation(from.getLocation());
        post.setStatus(1);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setFavoriteCount(0);

        postDao.insert(post);
    }

    public PostDetailVO getPostDetail(Long postId) {
        CoffeePost post = postDao.selectById(postId);
        if (post == null || post.getStatus() == 0) {
            throw new ServiceException("帖子不存在或者已删除");
        }

        // TODO: 通过 OpenFeign 获取发帖人信息
        String username = "未知用户";
        String avatar = null;

        // 查评论列表
        List<CoffeeComment> commentList = commentDao.selectList(new LambdaQueryWrapper<CoffeeComment>()
                .eq(CoffeeComment::getPostId, postId)
                .orderByAsc(CoffeeComment::getCreateTime)
        );

        // TODO: 批量查评论人信息（通过 OpenFeign）
        List<CommentVO> commentVOs = commentList.stream().map(c -> {
            return CommentVO.builder()
                    .id(c.getId())
                    .userId(c.getUserId())
                    .username("未知")
                    .avatar(null)
                    .content(c.getContent())
                    .createTime(c.getCreateTime())
                    .build();
        }).collect(Collectors.toList());

        // 查当前用户是否点赞/收藏
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean likedByMe = false;
        boolean favoritedByMe = false;

        if (currentUserId != null) {
            likedByMe = likeDao.selectCount(
                    new LambdaQueryWrapper<CoffeeLike>()
                            .eq(CoffeeLike::getPostId, postId)
                            .eq(CoffeeLike::getUserId, currentUserId)
            ) > 0;

            favoritedByMe = favoriteDao.selectCount(
                    new LambdaQueryWrapper<CoffeeFavorite>()
                            .eq(CoffeeFavorite::getPostId, postId)
                            .eq(CoffeeFavorite::getUserId, currentUserId)
            ) > 0;
        }

        return PostDetailVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .username(username)
                .avatar(avatar)
                .title(post.getTitle())
                .content(post.getContent())
                .images(parseImages(post.getImages()))
                .coffeeName(post.getCoffeeName())
                .coffeeBrand(post.getCoffeeBrand())
                .location(post.getLocation())
                .postType(1)
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .favoriteCount(post.getFavoriteCount())
                .likedByMe(likedByMe)
                .favoritedByMe(favoritedByMe)
                .createTime(post.getCreateTime())
                .comments(commentVOs)
                .build();
    }

    public List<PostListVO> search(String keyword, Integer page, Integer size) {
        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeePost::getStatus, 1)
                .and(w -> w.like(CoffeePost::getTitle, keyword)
                        .or()
                        .like(CoffeePost::getCoffeeName, keyword)
                        .or()
                        .like(CoffeePost::getCoffeeBrand, keyword)
                        .or()
                        .like(CoffeePost::getContent, keyword))
                .orderByDesc(CoffeePost::getCreateTime)
                .orderByDesc(CoffeePost::getId)
                .last("LIMIT " + (page - 1) * size + "," + size);
        return buildPostListVO(postDao.selectList(wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean toggleLike(Long postId) {
        Long userId = SecurityUtils.requireUserId();

        Long count = likeDao.selectCount(new LambdaQueryWrapper<CoffeeLike>()
                .eq(CoffeeLike::getPostId, postId)
                .eq(CoffeeLike::getUserId, userId)
        );

        if (count > 0) {
            // 取消点赞
            likeDao.delete(new LambdaQueryWrapper<CoffeeLike>()
                    .eq(CoffeeLike::getPostId, postId)
                    .eq(CoffeeLike::getUserId, userId)
            );
            CoffeePost post = postDao.selectById(postId);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postDao.updateById(post);
            // TODO: 发送通知到 wc-message
            return false;
        } else {
            // 点赞
            CoffeeLike like = new CoffeeLike();
            like.setPostId(postId);
            like.setUserId(userId);
            likeDao.insert(like);

            CoffeePost post = postDao.selectById(postId);
            post.setLikeCount(post.getLikeCount() + 1);
            postDao.updateById(post);
            // TODO: 发送通知到 wc-message
            return true;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean toggleFavorite(Long postId) {
        Long userId = SecurityUtils.requireUserId();

        Long count = favoriteDao.selectCount(new LambdaQueryWrapper<CoffeeFavorite>()
                .eq(CoffeeFavorite::getPostId, postId)
                .eq(CoffeeFavorite::getUserId, userId)
        );

        if (count == 0) {
            // 收藏
            CoffeeFavorite favorite = new CoffeeFavorite();
            favorite.setPostId(postId);
            favorite.setUserId(userId);
            favoriteDao.insert(favorite);

            CoffeePost post = postDao.selectById(postId);
            post.setFavoriteCount(post.getFavoriteCount() + 1);
            postDao.updateById(post);
            // TODO: 发送通知到 wc-message
            return true;
        } else {
            // 取消收藏
            favoriteDao.delete(new LambdaQueryWrapper<CoffeeFavorite>()
                    .eq(CoffeeFavorite::getPostId, postId)
                    .eq(CoffeeFavorite::getUserId, userId)
            );
            CoffeePost post = postDao.selectById(postId);
            post.setFavoriteCount(Math.max(0, post.getFavoriteCount() - 1));
            postDao.updateById(post);
            return false;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentVO addComment(Long postId, CommentCreateFrom from) {
        Long userId = SecurityUtils.requireUserId();

        CoffeePost post = postDao.selectById(postId);
        if (post == null || post.getStatus() == 0) {
            throw new ServiceException("帖子不存在或者已删除");
        }

        CoffeeComment comment = new CoffeeComment();
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setContent(from.getContent());
        commentDao.insert(comment);

        post.setCommentCount(post.getCommentCount() + 1);
        postDao.updateById(post);

        // TODO: 通过 OpenFeign 获取当前用户信息
        String username = "未知";
        String avatar = null;

        // TODO: 发送通知到 wc-message

        return CommentVO.builder()
                .id(comment.getId())
                .userId(userId)
                .username(username)
                .avatar(avatar)
                .content(from.getContent())
                .createTime(comment.getCreateTime())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        Long userId = SecurityUtils.requireUserId();

        CoffeePost post = postDao.selectById(id);
        if (post == null) throw new ServiceException("帖子不存在");
        if (!post.getUserId().equals(userId)) throw new ServiceException("这不是你的帖子");

        post.setStatus(0);
        postDao.updateById(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePost(Long id, PostCreateFrom from) {
        Long userId = SecurityUtils.requireUserId();

        CoffeePost post = postDao.selectById(id);
        if (post == null) throw new ServiceException("帖子不存在");
        if (!post.getUserId().equals(userId)) throw new ServiceException("这不是你发布的帖子");

        post.setTitle(from.getTitle());
        post.setContent(from.getContent());
        post.setCoffeeName(from.getCoffeeName());
        post.setCoffeeBrand(from.getCoffeeBrand());
        post.setLocation(from.getLocation());
        if (from.getImages() != null && !from.getImages().isEmpty()) {
            post.setImages(JSONUtil.toJsonStr(from.getImages()));
        }

        postDao.updateById(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId) {
        Long userId = SecurityUtils.requireUserId();

        CoffeeComment comment = commentDao.selectById(commentId);
        if (comment == null) throw new ServiceException("评论不存在");

        CoffeePost post = postDao.selectById(comment.getPostId());

        boolean isCommentAuthor = comment.getUserId().equals(userId);
        boolean isPostAuthor = post.getUserId().equals(userId);
        if (!isCommentAuthor && !isPostAuthor) {
            throw new ServiceException("无权删除该评论");
        }

        commentDao.deleteById(commentId);
        post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
        postDao.updateById(post);
    }

    public List<PostListVO> getMyPosts(Integer page, Integer size) {
        Long userId = SecurityUtils.requireUserId();

        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeePost::getUserId, userId)
                .eq(CoffeePost::getStatus, 1)
                .orderByDesc(CoffeePost::getCreateTime)
                .orderByDesc(CoffeePost::getId)
                .last("LIMIT " + (page - 1) * size + "," + size);
        return buildPostListVO(postDao.selectList(wrapper));
    }

    public List<PostListVO> getMyFavorites(Integer page, Integer size) {
        Long userId = SecurityUtils.requireUserId();

        List<Long> allPostIds = favoriteDao.selectList(
                new LambdaQueryWrapper<CoffeeFavorite>()
                        .eq(CoffeeFavorite::getUserId, userId)
                        .orderByDesc(CoffeeFavorite::getCreateTime)
        ).stream().map(CoffeeFavorite::getPostId).collect(Collectors.toList());

        if (allPostIds.isEmpty()) return List.of();

        int start = (page - 1) * size;
        if (start >= allPostIds.size()) return List.of();
        int end = Math.min(start + size, allPostIds.size());
        List<Long> postIds = allPostIds.subList(start, end);

        List<CoffeePost> posts = postDao.selectList(
                new LambdaQueryWrapper<CoffeePost>()
                        .in(CoffeePost::getId, postIds)
                        .eq(CoffeePost::getStatus, 1)
                        .orderByDesc(CoffeePost::getCreateTime)
        );

        return buildPostListVO(posts);
    }

    public List<PostListVO> getMyLikes(Integer page, Integer size) {
        Long userId = SecurityUtils.requireUserId();

        List<Long> allPostIds = likeDao.selectList(
                new LambdaQueryWrapper<CoffeeLike>()
                        .eq(CoffeeLike::getUserId, userId)
                        .orderByDesc(CoffeeLike::getCreateTime)
        ).stream().map(CoffeeLike::getPostId).collect(Collectors.toList());

        if (allPostIds.isEmpty()) return List.of();

        int start = (page - 1) * size;
        if (start >= allPostIds.size()) return List.of();
        int end = Math.min(start + size, allPostIds.size());
        List<Long> postIds = allPostIds.subList(start, end);

        List<CoffeePost> posts = postDao.selectList(
                new LambdaQueryWrapper<CoffeePost>()
                        .in(CoffeePost::getId, postIds)
                        .eq(CoffeePost::getStatus, 1)
                        .orderByDesc(CoffeePost::getCreateTime)
        );

        return buildPostListVO(posts);
    }

    public String uploadImage(MultipartFile file) {
        if (file.isEmpty()) throw new ServiceException("文件不能为空");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ServiceException("只能上传图片");
        }

        String originalName = file.getOriginalFilename();
        String suffix = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : ".png";
        String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + suffix;

        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (!created) throw new ServiceException("无法创建上传目录");
        }

        try {
            file.transferTo(new File(uploadDir, fileName));
        } catch (IOException e) {
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }
        return "http://localhost:8083/uploads/" + fileName;
    }

    public List<PostListVO> getHotPosts(Integer page, Integer size) {
        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeePost::getStatus, 1)
                .last("ORDER BY (like_count + comment_count + favorite_count) DESC, create_time DESC, id DESC LIMIT "
                        + (page - 1) * size + "," + size);
        return buildPostListVO(postDao.selectList(wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean toggleCommentLike(Long commentId) {
        Long userId = SecurityUtils.requireUserId();

        LambdaQueryWrapper<CoffeeCommentLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoffeeCommentLike::getCommentId, commentId)
                .eq(CoffeeCommentLike::getUserId, userId);
        Long count = commentLikeDao.selectCount(wrapper);

        if (count > 0) {
            commentLikeDao.delete(wrapper);
            CoffeeComment comment = commentDao.selectById(commentId);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            commentDao.updateById(comment);
            return false;
        } else {
            CoffeeCommentLike like = new CoffeeCommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            commentLikeDao.insert(like);

            CoffeeComment comment = commentDao.selectById(commentId);
            comment.setLikeCount(comment.getLikeCount() + 1);
            commentDao.updateById(comment);
            // TODO: 发送通知到 wc-message
            return true;
        }
    }

    public List<PostListVO> getFollowingPosts(Integer page, Integer size) {
        Long userId = SecurityUtils.requireUserId();

        List<Long> followeeIds = followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
        ).stream().map(UserFollow::getFolloweeId).collect(Collectors.toList());

        if (followeeIds.isEmpty()) return List.of();

        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CoffeePost::getUserId, followeeIds)
                .eq(CoffeePost::getStatus, 1)
                .orderByDesc(CoffeePost::getCreateTime)
                .orderByDesc(CoffeePost::getId)
                .last("LIMIT " + (page - 1) * size + "," + size);
        return buildPostListVO(postDao.selectList(wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reportPost(Long postId, ReportCreatFrom from) {
        Long userId = SecurityUtils.requireUserId();

        CoffeePost post = postDao.selectById(postId);
        if (post == null || post.getStatus() == 0) throw new ServiceException("帖子不存在");
        if (post.getUserId().equals(userId)) throw new ServiceException("不能举报自己的帖子");

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

        postReportDao.insert(report);
    }
}
