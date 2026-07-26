package cn.lx.worldcoffee.community.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.community.domain.from.CommentCreateFrom;
import cn.lx.worldcoffee.community.domain.from.FavoriteCollectionForm;
import cn.lx.worldcoffee.community.domain.from.FeedEventCreateFrom;
import cn.lx.worldcoffee.community.domain.from.NotInterestedForm;
import cn.lx.worldcoffee.community.domain.from.PostCreateFrom;
import cn.lx.worldcoffee.community.domain.from.ReportCreatFrom;
import cn.lx.worldcoffee.community.domain.from.ReportHandleFrom;
import cn.lx.worldcoffee.community.domain.vo.CommentVO;
import cn.lx.worldcoffee.community.domain.vo.CreatorStatsVO;
import cn.lx.worldcoffee.community.domain.vo.FavoriteCollectionVO;
import cn.lx.worldcoffee.community.domain.vo.PostDetailVO;
import cn.lx.worldcoffee.community.domain.vo.PostDraftVO;
import cn.lx.worldcoffee.community.domain.vo.PostListVO;
import cn.lx.worldcoffee.community.domain.vo.ReportReviewVO;
import cn.lx.worldcoffee.community.domain.vo.TopicVO;
import cn.lx.worldcoffee.community.domain.vo.UnifiedSearchVO;
import cn.lx.worldcoffee.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "咖啡社区模块", description = "帖子 CRUD、点赞收藏、评论、图片上传、搜索")
@RestController
@RequestMapping("/api/coffee")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @Operation(summary = "首页帖子列表", description = "分页获取帖子，sort=latest按时间倒序，sort=random随机排序")
    @GetMapping("/posts")
    public Result<List<PostListVO>> listPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "排序方式: latest=最新, random=随机") @RequestParam(defaultValue = "latest") String sort){
        return Result.success(communityService.listPosts(page, size, sort));
    }

    @Operation(summary = "推荐 Feed", description = "按互动热度、新鲜度、关注关系和用户兴趣生成首页推荐流")
    @GetMapping("/posts/recommend")
    public Result<List<PostListVO>> recommendPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "匿名访问会话ID") @RequestParam(required = false) String sessionId) {
        return Result.success(communityService.recommendPosts(page, size, sessionId));
    }

    @Operation(summary = "记录 Feed 行为", description = "记录曝光、点击、停留和不感兴趣事件，用于推荐学习")
    @PostMapping("/feed-events")
    public Result<Void> recordFeedEvent(@Valid @RequestBody FeedEventCreateFrom from) {
        communityService.recordFeedEvent(from);
        return Result.success(null);
    }

    @Operation(summary = "不感兴趣", description = "显式隐藏该帖子，并作为推荐负反馈学习")
    @PostMapping("/posts/{id}/not-interested")
    public Result<Void> markNotInterested(
            @PathVariable Long id,
            @RequestBody(required = false) NotInterestedForm from) {
        communityService.markNotInterested(id, from);
        return Result.success(null);
    }

    @GetMapping("/topics")
    public Result<List<TopicVO>> listTopics(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(communityService.listTopics(keyword, limit));
    }

    @GetMapping("/posts/topic")
    public Result<List<PostListVO>> listPostsByTopic(
            @RequestParam String topic,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(communityService.listPostsByTopic(topic, page, size));
    }

    @GetMapping("/drafts/me")
    public Result<PostDraftVO> getMyDraft() {
        return Result.success(communityService.getMyDraft());
    }

    @PutMapping("/drafts/me")
    public Result<Void> saveMyDraft(@RequestBody PostCreateFrom from) {
        communityService.saveMyDraft(from);
        return Result.success(null);
    }

    @DeleteMapping("/drafts/me")
    public Result<Void> deleteMyDraft() {
        communityService.deleteMyDraft();
        return Result.success(null);
    }

    @GetMapping("/creator/stats")
    public Result<CreatorStatsVO> creatorStats() {
        return Result.success(communityService.getCreatorStats());
    }

    @Operation(summary = "发帖", description = "创建图文分享或打卡记录，需要登录")
    @PostMapping("/posts")
    public Result<Void> createPost(@Valid @RequestBody PostCreateFrom from){
        communityService.createPost(from);
        return Result.success(null);
    }

    @Operation(summary = "帖子详情", description = "获取帖子的完整信息 + 评论列表 + 当前用户点赞收藏状态")
    @GetMapping("/posts/{id}")
    public Result<PostDetailVO> postDetail(
            @Parameter(description = "帖子ID") @PathVariable Long id){
        return Result.success(communityService.getPostDetail(id));
    }

    @Operation(summary = "搜索帖子", description = "模糊匹配标题、咖啡名、品牌、内容四个字段")
    @GetMapping("/search")
    public Result<List<PostListVO>> search(
            @Parameter(description = "搜索关键词") @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size){
        return Result.success(communityService.search(keyword,page,size));
    }

    @GetMapping("/search/unified")
    public Result<UnifiedSearchVO> unifiedSearch(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(communityService.unifiedSearch(keyword, page, size));
    }

    @Operation(summary = "点赞/取消点赞", description = "toggle 模式，返回 true=已点赞 false=已取消")
    @PostMapping("/posts/{id}/like")
    public Result<Boolean> toggleLike(
            @Parameter(description = "帖子ID") @PathVariable Long id){
        return Result.success(communityService.toggleLike(id));
    }

    @Operation(summary = "收藏/取消收藏", description = "toggle 模式，返回 true=已收藏 false=已取消")
    @PostMapping("/posts/{id}/favorite")
    public Result<Boolean> toggleFavorite(
            @Parameter(description = "帖子ID") @PathVariable Long id){
        return Result.success(communityService.toggleFavorite(id));
    }

    @GetMapping("/collections")
    public Result<List<FavoriteCollectionVO>> listCollections() {
        return Result.success(communityService.listMyCollections());
    }

    @PostMapping("/collections")
    public Result<FavoriteCollectionVO> createCollection(@Valid @RequestBody FavoriteCollectionForm form) {
        return Result.success(communityService.createCollection(form));
    }

    @PutMapping("/collections/{id}")
    public Result<FavoriteCollectionVO> updateCollection(
            @PathVariable Long id,
            @Valid @RequestBody FavoriteCollectionForm form) {
        return Result.success(communityService.updateCollection(id, form));
    }

    @DeleteMapping("/collections/{id}")
    public Result<Void> deleteCollection(@PathVariable Long id) {
        communityService.deleteCollection(id);
        return Result.success(null);
    }

    @PostMapping("/collections/{id}/posts/{postId}")
    public Result<Void> addPostToCollection(@PathVariable Long id, @PathVariable Long postId) {
        communityService.addPostToCollection(id, postId);
        return Result.success(null);
    }

    @DeleteMapping("/collections/{id}/posts/{postId}")
    public Result<Void> removePostFromCollection(@PathVariable Long id, @PathVariable Long postId) {
        communityService.removePostFromCollection(id, postId);
        return Result.success(null);
    }

    @GetMapping("/collections/{id}/posts")
    public Result<List<PostListVO>> listCollectionPosts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(communityService.listCollectionPosts(id, page, size));
    }

    @Operation(summary = "发表评论", description = "对帖子发表评论，需要登录")
    @PostMapping("/posts/{id}/comment")
    public Result<CommentVO> addComment(
            @Parameter(description = "帖子ID") @PathVariable Long id,
            @Valid @RequestBody CommentCreateFrom from){
        return Result.success(communityService.addComment(id,from));
    }

    @Operation(summary = "删除帖子", description = "软删除，只能删自己的帖子")
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(
            @Parameter(description = "帖子ID") @PathVariable Long id){
        communityService.deletePost(id);
        return Result.success(null);
    }

    @Operation(summary = "修改帖子", description = "只能修改自己发布的帖子")
    @PutMapping("/posts/{id}")
    public Result<Void> updatePost(
            @Parameter(description = "帖子ID") @PathVariable Long id,
            @Valid @RequestBody PostCreateFrom from){
        communityService.updatePost(id,from);
        return Result.success(null);
    }

    @Operation(summary = "删除评论", description = "评论作者或帖子作者都可以删除")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(
            @Parameter(description = "评论ID") @PathVariable Long id){
        communityService.deleteComment(id);
        return Result.success(null);
    }

    @Operation(summary = "我的帖子", description = "查看当前用户发布的帖子列表")
    @GetMapping("/posts/my")
    public Result<List<PostListVO>> getMyPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size){
        return Result.success(communityService.getMyPosts(page,size));
    }

    @Operation(summary = "我的收藏", description = "查看当前用户收藏的帖子列表")
    @GetMapping("/favorites/my")
    public Result<List<PostListVO>> getMyFavorites(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(communityService.getMyFavorites(page, size));
    }

    @Operation(summary = "我的点赞", description = "查看当前用户点赞过的帖子列表")
    @GetMapping("/likes/my")
    public Result<List<PostListVO>> getMyLikes(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(communityService.getMyLikes(page, size));
    }

    @Operation(summary = "上传图片", description = "上传图片文件，返回可访问的 URL 地址")
    @PostMapping(value = "/upload",produces = "application/json")
    public Result<String> uploadImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        return Result.success(communityService.uploadImage(file));
    }

    @Operation(summary = "热门帖子", description = "按 like_count + comment_count + favorite_count 综合排序")
    @GetMapping("/posts/hot")
    public Result<List<PostListVO>> getHotPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(communityService.getHotPosts(page, size));
    }

    @Operation(summary = "评论点赞/取消", description = "对评论进行点赞 toggle")
    @PostMapping("/comments/{id}/like")
    public Result<Boolean> toggleCommentLike(
            @Parameter(description = "评论ID") @PathVariable Long id) {
        return Result.success(communityService.toggleCommentLike(id));
    }

    @Operation(summary = "关注动态", description = "查看关注的人发布的帖子，按时间倒序")
    @GetMapping("/posts/following")
    public Result<List<PostListVO>> getFollowingPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(communityService.getFollowingPosts(page,size));
    }

    @Operation(summary = "举报帖子", description = "举报违规帖子，需要登录，同一人同一帖只能举报一次")
    @PostMapping("/posts/{id}/report")
    public Result<Void> reportPost(
            @Parameter(description = "帖子ID") @PathVariable Long id,
            @Valid @RequestBody ReportCreatFrom from) {
        communityService.reportPost(id, from);
        return Result.success(null);
    }
    @GetMapping("/admin/community/reports")
    public Result<List<ReportReviewVO>> listReports(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(communityService.listReports(status, page, size));
    }

    @PostMapping("/admin/community/reports/{id}/handle")
    public Result<Void> handleReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportHandleFrom from) {
        communityService.handleReport(id, from);
        return Result.success(null);
    }
}
