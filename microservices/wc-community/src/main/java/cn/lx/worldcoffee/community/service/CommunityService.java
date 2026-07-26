package cn.lx.worldcoffee.community.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.common.storage.FileStorageService;
import cn.lx.worldcoffee.community.dao.*;
import cn.lx.worldcoffee.community.domain.*;
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
import cn.lx.worldcoffee.community.feign.UserFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private static final String EVENT_IMPRESSION = "IMPRESSION";
    private static final String EVENT_CLICK = "CLICK";
    private static final String EVENT_DWELL = "DWELL";
    private static final String EVENT_DISLIKE = "DISLIKE";

    private final CoffeePostDao postDao;
    private final CoffeeLikeDao likeDao;
    private final CoffeeFavoriteDao favoriteDao;
    private final CoffeeCommentDao commentDao;
    private final CoffeeCommentLikeDao commentLikeDao;
    private final UserFollowDao followDao;
    private final PostReportDao postReportDao;
    private final FeedEventDao feedEventDao;
    private final CoffeeTopicDao topicDao;
    private final CoffeePostTopicDao postTopicDao;
    private final PostDraftDao draftDao;
    private final FavoriteCollectionDao favoriteCollectionDao;
    private final FavoriteCollectionItemDao favoriteCollectionItemDao;
    private final PostProductDao postProductDao;
    private final PostNegativeFeedbackDao postNegativeFeedbackDao;
    private final FileStorageService fileStorageService;
    private final UserFeignClient userFeignClient;

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

    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSONUtil.toList(json, Long.class).stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJsonLongList(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        List<Long> normalized = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(20)
                .collect(Collectors.toList());
        return normalized.isEmpty() ? null : JSONUtil.toJsonStr(normalized);
    }

    private String normalizeNoteType(String noteType) {
        if (noteType == null || noteType.isBlank()) return "IMAGE";
        String normalized = noteType.trim().toUpperCase(Locale.ROOT);
        return "VIDEO".equals(normalized) ? "VIDEO" : "IMAGE";
    }

    private Map<Long, UserFeignClient.UserInfo> batchGetUserInfo(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return Map.of();
        try {
            return userFeignClient.batchGetUsers(ids);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<Long, List<String>> batchGetPostTopics(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        List<CoffeePostTopic> relations = postTopicDao.selectList(new LambdaQueryWrapper<CoffeePostTopic>()
                .in(CoffeePostTopic::getPostId, postIds));
        if (relations.isEmpty()) return Map.of();

        Set<Long> topicIds = relations.stream()
                .map(CoffeePostTopic::getTopicId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (topicIds.isEmpty()) return Map.of();

        Map<Long, String> topicNameMap = topicDao.selectList(new LambdaQueryWrapper<CoffeeTopic>()
                        .in(CoffeeTopic::getId, topicIds))
                .stream()
                .collect(Collectors.toMap(CoffeeTopic::getId, CoffeeTopic::getName, (a, b) -> a));

        Map<Long, List<String>> result = new HashMap<>();
        for (CoffeePostTopic relation : relations) {
            String name = topicNameMap.get(relation.getTopicId());
            if (name != null) {
                result.computeIfAbsent(relation.getPostId(), k -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    private Map<Long, List<Long>> batchGetPostProducts(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        return postProductDao.selectList(new LambdaQueryWrapper<PostProduct>()
                        .in(PostProduct::getPostId, postIds))
                .stream()
                .filter(item -> item.getPostId() != null && item.getProductId() != null)
                .collect(Collectors.groupingBy(
                        PostProduct::getPostId,
                        Collectors.mapping(PostProduct::getProductId, Collectors.toList())
                ));
    }

    /**
     * 把帖子列表增强为 PostListVO 列表
     * 批量查用户名、点赞状态、收藏状态，避免 N+1 问题
     */
    public List<PostListVO> buildPostListVO(List<CoffeePost> posts) {
        if (posts.isEmpty()) return List.of();

        List<Long> allPostIds = posts.stream().map(CoffeePost::getId).collect(Collectors.toList());
        Map<Long, List<String>> topicMap = batchGetPostTopics(allPostIds);
        Map<Long, List<Long>> productMap = batchGetPostProducts(allPostIds);
        Map<Long, UserFeignClient.UserInfo> userInfoMap = batchGetUserInfo(
                posts.stream().map(CoffeePost::getUserId).collect(Collectors.toSet())
        );

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
                    .noteType(normalizeNoteType(post.getNoteType()))
                    .videoUrl(post.getVideoUrl())
                    .coverUrl(post.getCoverUrl())
                    .videoDuration(post.getVideoDuration())
                    .coffeeName(post.getCoffeeName())
                    .coffeeBrand(post.getCoffeeBrand())
                    .location(post.getLocation())
                    .topics(topicMap.getOrDefault(post.getId(), List.of()))
                    .productIds(productMap.getOrDefault(post.getId(), List.of()))
                    .likeCount(post.getLikeCount())
                    .commentCount(post.getCommentCount())
                    .favoriteCount(post.getFavoriteCount())
                    .username(Optional.ofNullable(userInfoMap.get(post.getUserId()))
                            .map(UserFeignClient.UserInfo::username)
                            .orElse("未知用户"))
                    .avatar(Optional.ofNullable(userInfoMap.get(post.getUserId()))
                            .map(UserFeignClient.UserInfo::avatar)
                            .orElse(null))
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

    public List<PostListVO> recommendPosts(Integer page, Integer size, String sessionId) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        int start = (safePage - 1) * safeSize;
        int rankLimit = Math.min(Math.max(safePage * safeSize * 8, 80), 500);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String safeSessionId = normalizeSessionId(sessionId);
        Set<Long> hiddenPostIds = getHiddenPostIds(currentUserId, safeSessionId);

        LambdaQueryWrapper<CoffeePost> candidateWrapper = new LambdaQueryWrapper<CoffeePost>()
                .eq(CoffeePost::getStatus, 1);
        if (!hiddenPostIds.isEmpty()) {
            candidateWrapper.notIn(CoffeePost::getId, hiddenPostIds);
        }
        candidateWrapper.orderByDesc(CoffeePost::getCreateTime)
                .orderByDesc(CoffeePost::getId)
                .last("LIMIT " + rankLimit);

        List<CoffeePost> candidates = postDao.selectList(candidateWrapper);
        if (candidates.isEmpty() || start >= candidates.size()) return List.of();

        Set<Long> followeeIds = getFolloweeIds(currentUserId);
        Map<String, Integer> interestProfile = buildInterestProfile(currentUserId, safeSessionId);
        Map<Long, Integer> exposureCounts = getExposureCounts(currentUserId, safeSessionId);

        List<CoffeePost> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator
                .comparingDouble((CoffeePost post) -> scoreRecommendation(post, currentUserId, followeeIds, interestProfile, exposureCounts))
                .reversed()
                .thenComparing(CoffeePost::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(CoffeePost::getId, Comparator.nullsLast(Comparator.reverseOrder())));

        int end = Math.min(start + safeSize, ranked.size());
        return buildPostListVO(ranked.subList(start, end));
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) return 10;
        return Math.min(Math.max(size, 1), 50);
    }

    private Set<Long> getFolloweeIds(Long userId) {
        if (userId == null) return Set.of();
        return followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId))
                .stream()
                .map(UserFollow::getFolloweeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<String, Integer> buildInterestProfile(Long userId, String sessionId) {
        if (userId == null && sessionId == null) return Map.of();

        Map<Long, Integer> postWeights = new LinkedHashMap<>();
        if (userId != null) {
            likeDao.selectList(new LambdaQueryWrapper<CoffeeLike>()
                            .eq(CoffeeLike::getUserId, userId)
                            .orderByDesc(CoffeeLike::getCreateTime)
                            .last("LIMIT 50"))
                    .forEach(like -> postWeights.merge(like.getPostId(), 3, Integer::sum));
            favoriteDao.selectList(new LambdaQueryWrapper<CoffeeFavorite>()
                            .eq(CoffeeFavorite::getUserId, userId)
                            .orderByDesc(CoffeeFavorite::getCreateTime)
                            .last("LIMIT 50"))
                    .forEach(favorite -> postWeights.merge(favorite.getPostId(), 5, Integer::sum));
        }

        getRecentFeedEvents(userId, sessionId, 200).forEach(event -> {
            String type = event.getEventType();
            int weight = EVENT_CLICK.equals(type) ? 4
                    : EVENT_DWELL.equals(type) ? dwellWeight(event.getDwellMs())
                    : EVENT_DISLIKE.equals(type) ? -20
                    : 0;
            if (weight != 0 && event.getPostId() != null) {
                postWeights.merge(event.getPostId(), weight, Integer::sum);
            }
        });
        if (postWeights.isEmpty()) return Map.of();

        List<CoffeePost> interactedPosts = postDao.selectList(new LambdaQueryWrapper<CoffeePost>()
                .in(CoffeePost::getId, postWeights.keySet())
                .eq(CoffeePost::getStatus, 1));
        if (interactedPosts.isEmpty()) return Map.of();

        Map<String, Integer> profile = new HashMap<>();
        for (CoffeePost post : interactedPosts) {
            int baseWeight = postWeights.getOrDefault(post.getId(), 1);
            addInterest(profile, post.getCoffeeName(), baseWeight * 5);
            addInterest(profile, post.getCoffeeBrand(), baseWeight * 4);
            addInterest(profile, post.getLocation(), baseWeight * 2);
            addInterest(profile, post.getTitle(), baseWeight * 2);
            addInterest(profile, post.getContent(), baseWeight);
        }
        return profile;
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordFeedEvent(FeedEventCreateFrom from) {
        String eventType = normalizeEventType(from.getEventType());
        CoffeePost post = postDao.selectById(from.getPostId());
        if (post == null || post.getStatus() == null || post.getStatus() == 0) {
            throw new ServiceException("post not found");
        }

        FeedEvent event = new FeedEvent();
        event.setUserId(SecurityUtils.getCurrentUserId());
        event.setSessionId(normalizeSessionId(from.getSessionId()));
        event.setPostId(from.getPostId());
        event.setEventType(eventType);
        event.setSource(normalizeSource(from.getSource()));
        event.setDwellMs(EVENT_DWELL.equals(eventType) ? normalizeDwellMs(from.getDwellMs()) : null);
        event.setCreateTime(LocalDateTime.now());
        feedEventDao.insert(event);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markNotInterested(Long postId, NotInterestedForm from) {
        CoffeePost post = postDao.selectById(postId);
        if (post == null || post.getStatus() == null || post.getStatus() == 0) {
            throw new ServiceException("post not found");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        String sessionId = normalizeSessionId(from == null ? null : from.getSessionId());

        PostNegativeFeedback feedback = new PostNegativeFeedback();
        feedback.setUserId(userId);
        feedback.setSessionId(sessionId);
        feedback.setPostId(postId);
        feedback.setReasonType(normalizeReasonType(from == null ? null : from.getReasonType()));
        feedback.setReason(from == null ? null : from.getReason());
        feedback.setCreateTime(LocalDateTime.now());
        postNegativeFeedbackDao.insert(feedback);

        FeedEvent event = new FeedEvent();
        event.setUserId(userId);
        event.setSessionId(sessionId);
        event.setPostId(postId);
        event.setEventType(EVENT_DISLIKE);
        event.setSource("not_interested");
        event.setCreateTime(LocalDateTime.now());
        feedEventDao.insert(event);
    }

    private String normalizeReasonType(String reasonType) {
        if (reasonType == null || reasonType.isBlank()) return "OTHER";
        String normalized = reasonType.trim().toUpperCase(Locale.ROOT);
        return normalized.length() > 30 ? normalized.substring(0, 30) : normalized;
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null) throw new ServiceException("eventType is required");
        String normalized = eventType.trim().toUpperCase(Locale.ROOT);
        if (EVENT_IMPRESSION.equals(normalized)
                || EVENT_CLICK.equals(normalized)
                || EVENT_DWELL.equals(normalized)
                || EVENT_DISLIKE.equals(normalized)) {
            return normalized;
        }
        throw new ServiceException("unsupported feed event type");
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "feed";
        String normalized = source.trim();
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }

    private Long normalizeDwellMs(Long dwellMs) {
        if (dwellMs == null || dwellMs < 0) return 0L;
        return Math.min(dwellMs, 30 * 60 * 1000L);
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        String normalized = sessionId.trim();
        if (normalized.length() < 8) return null;
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private int dwellWeight(Long dwellMs) {
        long seconds = dwellMs == null ? 0 : dwellMs / 1000;
        if (seconds >= 30) return 14;
        if (seconds >= 10) return 9;
        if (seconds >= 3) return 5;
        return 1;
    }

    private List<FeedEvent> getRecentFeedEvents(Long userId, String sessionId, int limit) {
        if (userId == null && sessionId == null) return List.of();
        LambdaQueryWrapper<FeedEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
                    if (userId != null) {
                        w.eq(FeedEvent::getUserId, userId);
                        if (sessionId != null) w.or().eq(FeedEvent::getSessionId, sessionId);
                    } else {
                        w.eq(FeedEvent::getSessionId, sessionId);
                    }
                })
                .orderByDesc(FeedEvent::getCreateTime)
                .orderByDesc(FeedEvent::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        return feedEventDao.selectList(wrapper);
    }

    private Set<Long> getHiddenPostIds(Long userId, String sessionId) {
        Set<Long> hidden = getRecentFeedEvents(userId, sessionId, 300).stream()
                .filter(event -> EVENT_DISLIKE.equals(event.getEventType()))
                .map(FeedEvent::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userId != null || sessionId != null) {
            LambdaQueryWrapper<PostNegativeFeedback> wrapper = new LambdaQueryWrapper<>();
            wrapper.and(w -> {
                        if (userId != null) {
                            w.eq(PostNegativeFeedback::getUserId, userId);
                            if (sessionId != null) w.or().eq(PostNegativeFeedback::getSessionId, sessionId);
                        } else {
                            w.eq(PostNegativeFeedback::getSessionId, sessionId);
                        }
                    })
                    .orderByDesc(PostNegativeFeedback::getCreateTime)
                    .last("LIMIT 500");
            postNegativeFeedbackDao.selectList(wrapper).stream()
                    .map(PostNegativeFeedback::getPostId)
                    .filter(Objects::nonNull)
                    .forEach(hidden::add);
        }
        return hidden;
    }

    private Map<Long, Integer> getExposureCounts(Long userId, String sessionId) {
        Map<Long, Integer> result = new HashMap<>();
        getRecentFeedEvents(userId, sessionId, 300).stream()
                .filter(event -> EVENT_IMPRESSION.equals(event.getEventType()))
                .map(FeedEvent::getPostId)
                .filter(Objects::nonNull)
                .forEach(postId -> result.merge(postId, 1, Integer::sum));
        return result;
    }

    private void addInterest(Map<String, Integer> profile, String text, int weight) {
        for (String keyword : extractKeywords(text)) {
            profile.merge(keyword, weight, Integer::sum);
        }
    }

    private List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        List<String> keywords = new ArrayList<>();
        if (normalized.length() >= 2 && normalized.length() <= 32) {
            keywords.add(normalized);
        }
        String[] parts = normalized.split("[\\s,./|:;!?()\\[\\]]+");
        for (String part : parts) {
            if (part.length() >= 2 && part.length() <= 32) {
                keywords.add(part);
            }
        }
        return keywords.stream().distinct().collect(Collectors.toList());
    }

    private double scoreRecommendation(CoffeePost post, Long userId, Set<Long> followeeIds,
                                       Map<String, Integer> interestProfile, Map<Long, Integer> exposureCounts) {
        double score = scoreCount(post.getLikeCount()) * 3
                + scoreCount(post.getFavoriteCount()) * 5
                + scoreCount(post.getCommentCount()) * 4;
        score += freshnessScore(post.getCreateTime());
        score += interestScore(post, interestProfile);

        if (post.getImages() != null && !post.getImages().isBlank()) score += 5;
        if (post.getUserId() != null && followeeIds.contains(post.getUserId())) score += 40;
        if (userId != null && Objects.equals(post.getUserId(), userId)) score -= 12;
        score -= Math.min(exposureCounts.getOrDefault(post.getId(), 0) * 4, 24);

        return score + stableJitter(userId, post.getId());
    }

    private int scoreCount(Integer count) {
        return count == null ? 0 : Math.max(count, 0);
    }

    private double freshnessScore(LocalDateTime createTime) {
        if (createTime == null) return 0;
        long hours = Math.max(0, ChronoUnit.HOURS.between(createTime, LocalDateTime.now()));
        if (hours <= 24) return 30;
        if (hours <= 72) return 18;
        if (hours <= 168) return 10;
        if (hours <= 720) return 4;
        return 0;
    }

    private double interestScore(CoffeePost post, Map<String, Integer> interestProfile) {
        if (interestProfile.isEmpty()) return 0;
        String haystack = String.join(" ",
                Optional.ofNullable(post.getTitle()).orElse(""),
                Optional.ofNullable(post.getContent()).orElse(""),
                Optional.ofNullable(post.getCoffeeName()).orElse(""),
                Optional.ofNullable(post.getCoffeeBrand()).orElse(""),
                Optional.ofNullable(post.getLocation()).orElse("")
        ).toLowerCase(Locale.ROOT);

        int score = 0;
        for (Map.Entry<String, Integer> entry : interestProfile.entrySet()) {
            if (haystack.contains(entry.getKey())) {
                score += Math.min(entry.getValue(), 20);
            }
        }
        return Math.min(score, 80);
    }

    private double stableJitter(Long userId, Long postId) {
        long userSeed = userId == null ? 17L : userId;
        long postSeed = postId == null ? 31L : postId;
        long seed = userSeed * 1103515245L + postSeed * 2654435761L;
        return Math.floorMod(seed, 1000) / 1000.0 * 8;
    }

    public List<TopicVO> listTopics(String keyword, Integer limit) {
        int safeLimit = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
        LambdaQueryWrapper<CoffeeTopic> wrapper = new LambdaQueryWrapper<CoffeeTopic>()
                .eq(CoffeeTopic::getStatus, 1);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(CoffeeTopic::getName, keyword.trim());
        }
        wrapper.orderByDesc(CoffeeTopic::getPostCount)
                .orderByDesc(CoffeeTopic::getUpdateTime)
                .last("LIMIT " + safeLimit);
        return topicDao.selectList(wrapper).stream()
                .map(this::toTopicVO)
                .collect(Collectors.toList());
    }

    public List<PostListVO> listPostsByTopic(String topic, Integer page, Integer size) {
        String topicName = normalizeTopicName(topic);
        if (topicName == null) return List.of();
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        CoffeeTopic item = topicDao.selectOne(new LambdaQueryWrapper<CoffeeTopic>()
                .eq(CoffeeTopic::getName, topicName)
                .eq(CoffeeTopic::getStatus, 1)
                .last("LIMIT 1"));
        if (item == null) return List.of();

        List<Long> postIds = postTopicDao.selectList(new LambdaQueryWrapper<CoffeePostTopic>()
                        .eq(CoffeePostTopic::getTopicId, item.getId()))
                .stream().map(CoffeePostTopic::getPostId).collect(Collectors.toList());
        if (postIds.isEmpty()) return List.of();

        LambdaQueryWrapper<CoffeePost> wrapper = new LambdaQueryWrapper<CoffeePost>()
                .in(CoffeePost::getId, postIds)
                .eq(CoffeePost::getStatus, 1)
                .orderByDesc(CoffeePost::getCreateTime)
                .orderByDesc(CoffeePost::getId)
                .last("LIMIT " + (safePage - 1) * safeSize + "," + safeSize);
        return buildPostListVO(postDao.selectList(wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMyDraft(PostCreateFrom from) {
        Long userId = SecurityUtils.requireUserId();
        PostDraft draft = draftDao.selectOne(new LambdaQueryWrapper<PostDraft>()
                .eq(PostDraft::getUserId, userId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (draft == null) {
            draft = new PostDraft();
            draft.setUserId(userId);
            draft.setCreateTime(now);
        }
        draft.setTitle(from.getTitle());
        draft.setContent(from.getContent());
        draft.setImages(toJsonList(from.getImages()));
        draft.setNoteType(normalizeNoteType(from.getNoteType()));
        draft.setVideoUrl(from.getVideoUrl());
        draft.setCoverUrl(from.getCoverUrl());
        draft.setVideoDuration(from.getVideoDuration());
        draft.setCoffeeName(from.getCoffeeName());
        draft.setCoffeeBrand(from.getCoffeeBrand());
        draft.setLocation(from.getLocation());
        draft.setTopics(toJsonList(normalizeTopicNames(from.getTopics())));
        draft.setProductIds(toJsonLongList(from.getProductIds()));
        draft.setUpdateTime(now);
        if (draft.getId() == null) {
            draftDao.insert(draft);
        } else {
            draftDao.updateById(draft);
        }
    }

    public PostDraftVO getMyDraft() {
        Long userId = SecurityUtils.requireUserId();
        PostDraft draft = draftDao.selectOne(new LambdaQueryWrapper<PostDraft>()
                .eq(PostDraft::getUserId, userId)
                .last("LIMIT 1"));
        return draft == null ? null : toDraftVO(draft);
    }

    public void deleteMyDraft() {
        Long userId = SecurityUtils.requireUserId();
        draftDao.delete(new LambdaQueryWrapper<PostDraft>().eq(PostDraft::getUserId, userId));
    }

    public List<ReportReviewVO> listReports(Integer status, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<PostReport> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(PostReport::getStatus, status);
        wrapper.orderByAsc(PostReport::getStatus)
                .orderByDesc(PostReport::getCreateTime)
                .last("LIMIT " + (safePage - 1) * safeSize + "," + safeSize);
        return postReportDao.selectList(wrapper).stream()
                .map(this::toReportReviewVO)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long reportId, ReportHandleFrom from) {
        PostReport report = postReportDao.selectById(reportId);
        if (report == null) throw new ServiceException("report not found");
        String action = from.getAction() == null ? "" : from.getAction().trim().toUpperCase(Locale.ROOT);
        if (!"IGNORE".equals(action) && !"REMOVE_POST".equals(action)) {
            throw new ServiceException("unsupported report action");
        }
        if ("REMOVE_POST".equals(action)) {
            CoffeePost post = postDao.selectById(report.getPostId());
            if (post != null) {
                post.setStatus(0);
                postDao.updateById(post);
                detachPostTopics(post.getId());
            }
        }
        report.setStatus("REMOVE_POST".equals(action) ? 2 : 1);
        report.setRemark(from.getRemark());
        report.setHandleTime(LocalDateTime.now());
        postReportDao.updateById(report);
    }

    private TopicVO toTopicVO(CoffeeTopic topic) {
        return TopicVO.builder()
                .id(topic.getId())
                .name(topic.getName())
                .description(topic.getDescription())
                .postCount(topic.getPostCount())
                .build();
    }

    private PostDraftVO toDraftVO(PostDraft draft) {
        return PostDraftVO.builder()
                .id(draft.getId())
                .title(draft.getTitle())
                .content(draft.getContent())
                .images(parseImages(draft.getImages()))
                .noteType(normalizeNoteType(draft.getNoteType()))
                .videoUrl(draft.getVideoUrl())
                .coverUrl(draft.getCoverUrl())
                .videoDuration(draft.getVideoDuration())
                .coffeeName(draft.getCoffeeName())
                .coffeeBrand(draft.getCoffeeBrand())
                .location(draft.getLocation())
                .topics(parseImages(draft.getTopics()))
                .productIds(parseLongList(draft.getProductIds()))
                .updateTime(draft.getUpdateTime())
                .build();
    }

    private ReportReviewVO toReportReviewVO(PostReport report) {
        CoffeePost post = postDao.selectById(report.getPostId());
        String statusText = report.getStatus() == null || report.getStatus() == 0 ? "pending"
                : report.getStatus() == 2 ? "post removed" : "ignored";
        return ReportReviewVO.builder()
                .id(report.getId())
                .postId(report.getPostId())
                .postTitle(post == null ? null : post.getTitle())
                .postContent(post == null ? null : post.getContent())
                .postImages(post == null ? List.of() : parseImages(post.getImages()))
                .postAuthorId(post == null ? null : post.getUserId())
                .reporterId(report.getReporterId())
                .reason(report.getReason())
                .status(report.getStatus())
                .statusText(statusText)
                .remark(report.getRemark())
                .createTime(report.getCreateTime())
                .handleTime(report.getHandleTime())
                .build();
    }

    private String toJsonList(List<String> list) {
        return list == null || list.isEmpty() ? null : JSONUtil.toJsonStr(list);
    }

    private List<String> normalizeTopicNames(List<String> topics) {
        if (topics == null) return List.of();
        return topics.stream()
                .map(this::normalizeTopicName)
                .filter(Objects::nonNull)
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
    }

    private String normalizeTopicName(String name) {
        if (name == null) return null;
        String normalized = name.trim().replace("#", "");
        if (normalized.isBlank()) return null;
        return normalized.length() > 30 ? normalized.substring(0, 30) : normalized;
    }

    private void replacePostTopics(Long postId, List<String> rawTopics) {
        List<CoffeePostTopic> oldRelations = postTopicDao.selectList(new LambdaQueryWrapper<CoffeePostTopic>()
                .eq(CoffeePostTopic::getPostId, postId));
        Set<Long> touchedTopicIds = oldRelations.stream()
                .map(CoffeePostTopic::getTopicId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        postTopicDao.delete(new LambdaQueryWrapper<CoffeePostTopic>().eq(CoffeePostTopic::getPostId, postId));
        List<String> topics = normalizeTopicNames(rawTopics);
        if (topics.isEmpty()) {
            refreshTopicPostCounts(touchedTopicIds);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (String name : topics) {
            CoffeeTopic topic = topicDao.selectOne(new LambdaQueryWrapper<CoffeeTopic>()
                    .eq(CoffeeTopic::getName, name)
                    .last("LIMIT 1"));
            if (topic == null) {
                topic = new CoffeeTopic();
                topic.setName(name);
                topic.setDescription("");
                topic.setPostCount(0);
                topic.setStatus(1);
                topic.setCreateTime(now);
                topic.setUpdateTime(now);
                topicDao.insert(topic);
            }
            CoffeePostTopic relation = new CoffeePostTopic();
            relation.setPostId(postId);
            relation.setTopicId(topic.getId());
            relation.setCreateTime(now);
            postTopicDao.insert(relation);
            touchedTopicIds.add(topic.getId());
        }
        refreshTopicPostCounts(touchedTopicIds);
    }

    private void detachPostTopics(Long postId) {
        List<CoffeePostTopic> oldRelations = postTopicDao.selectList(new LambdaQueryWrapper<CoffeePostTopic>()
                .eq(CoffeePostTopic::getPostId, postId));
        Set<Long> topicIds = oldRelations.stream()
                .map(CoffeePostTopic::getTopicId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        postTopicDao.delete(new LambdaQueryWrapper<CoffeePostTopic>().eq(CoffeePostTopic::getPostId, postId));
        refreshTopicPostCounts(topicIds);
    }

    private void refreshTopicPostCounts(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Long topicId : topicIds) {
            CoffeeTopic topic = topicDao.selectById(topicId);
            if (topic == null) continue;
            Long count = postTopicDao.selectCount(new LambdaQueryWrapper<CoffeePostTopic>()
                    .eq(CoffeePostTopic::getTopicId, topicId));
            topic.setPostCount(count == null ? 0 : count.intValue());
            topic.setUpdateTime(now);
            topicDao.updateById(topic);
        }
    }

    private void replacePostProducts(Long postId, Collection<Long> productIds) {
        postProductDao.delete(new LambdaQueryWrapper<PostProduct>().eq(PostProduct::getPostId, postId));
        if (productIds == null || productIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(20)
                .forEach(productId -> {
                    PostProduct item = new PostProduct();
                    item.setPostId(postId);
                    item.setProductId(productId);
                    item.setCreateTime(now);
                    postProductDao.insert(item);
                });
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
        post.setNoteType(normalizeNoteType(from.getNoteType()));
        post.setVideoUrl(from.getVideoUrl());
        post.setCoverUrl(from.getCoverUrl());
        post.setVideoDuration(from.getVideoDuration());
        post.setCoffeeName(from.getCoffeeName());
        post.setCoffeeBrand(from.getCoffeeBrand());
        post.setLocation(from.getLocation());
        post.setStatus(1);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setFavoriteCount(0);

        postDao.insert(post);
        replacePostTopics(post.getId(), from.getTopics());
        replacePostProducts(post.getId(), from.getProductIds());
        draftDao.delete(new LambdaQueryWrapper<PostDraft>().eq(PostDraft::getUserId, userId));
    }

    public PostDetailVO getPostDetail(Long postId) {
        CoffeePost post = postDao.selectById(postId);
        if (post == null || post.getStatus() == 0) {
            throw new ServiceException("帖子不存在或者已删除");
        }

        // 查评论列表
        List<CoffeeComment> commentList = commentDao.selectList(new LambdaQueryWrapper<CoffeeComment>()
                .eq(CoffeeComment::getPostId, postId)
                .orderByAsc(CoffeeComment::getCreateTime)
        );

        Set<Long> userIds = new HashSet<>();
        userIds.add(post.getUserId());
        commentList.stream().map(CoffeeComment::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        commentList.stream().map(CoffeeComment::getReplyToUserId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, UserFeignClient.UserInfo> userInfoMap = batchGetUserInfo(userIds);
        UserFeignClient.UserInfo postUser = userInfoMap.get(post.getUserId());
        String username = postUser != null && postUser.username() != null ? postUser.username() : "未知用户";
        String avatar = postUser != null ? postUser.avatar() : null;

        List<CommentVO> commentVOs = buildCommentTree(commentList, userInfoMap);

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
                .noteType(normalizeNoteType(post.getNoteType()))
                .videoUrl(post.getVideoUrl())
                .coverUrl(post.getCoverUrl())
                .videoDuration(post.getVideoDuration())
                .coffeeName(post.getCoffeeName())
                .coffeeBrand(post.getCoffeeBrand())
                .location(post.getLocation())
                .topics(batchGetPostTopics(List.of(postId)).getOrDefault(postId, List.of()))
                .productIds(batchGetPostProducts(List.of(postId)).getOrDefault(postId, List.of()))
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

    private List<CommentVO> buildCommentTree(List<CoffeeComment> comments,
                                             Map<Long, UserFeignClient.UserInfo> userInfoMap) {
        if (comments == null || comments.isEmpty()) return List.of();
        Map<Long, CommentVO> voMap = new LinkedHashMap<>();
        for (CoffeeComment comment : comments) {
            voMap.put(comment.getId(), toCommentVO(comment, userInfoMap, new ArrayList<>()));
        }

        List<CommentVO> roots = new ArrayList<>();
        for (CoffeeComment comment : comments) {
            CommentVO vo = voMap.get(comment.getId());
            Long parentId = comment.getParentId();
            if (parentId != null && voMap.containsKey(parentId)) {
                CommentVO parent = voMap.get(parentId);
                if (parent.getReplies() == null) {
                    parent.setReplies(new ArrayList<>());
                }
                parent.getReplies().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    private CommentVO toCommentVO(CoffeeComment comment,
                                  Map<Long, UserFeignClient.UserInfo> userInfoMap,
                                  List<CommentVO> replies) {
        UserFeignClient.UserInfo commentUser = userInfoMap.get(comment.getUserId());
        UserFeignClient.UserInfo replyToUser = comment.getReplyToUserId() == null
                ? null
                : userInfoMap.get(comment.getReplyToUserId());
        return CommentVO.builder()
                .id(comment.getId())
                .userId(comment.getUserId())
                .parentId(comment.getParentId())
                .rootId(comment.getRootId())
                .replyToUserId(comment.getReplyToUserId())
                .replyToUsername(replyToUser != null && replyToUser.username() != null ? replyToUser.username() : null)
                .username(commentUser != null && commentUser.username() != null ? commentUser.username() : "未知")
                .avatar(commentUser != null ? commentUser.avatar() : null)
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .replies(replies)
                .createTime(comment.getCreateTime())
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

    public UnifiedSearchVO unifiedSearch(String keyword, Integer page, Integer size) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.isBlank()) {
            return UnifiedSearchVO.builder()
                    .keyword("")
                    .posts(List.of())
                    .topics(listTopics(null, 10))
                    .build();
        }
        return UnifiedSearchVO.builder()
                .keyword(safeKeyword)
                .posts(search(safeKeyword, page, size))
                .topics(listTopics(safeKeyword, 10))
                .build();
    }

    public CreatorStatsVO getCreatorStats() {
        Long userId = SecurityUtils.requireUserId();
        List<CoffeePost> posts = postDao.selectList(new LambdaQueryWrapper<CoffeePost>()
                .eq(CoffeePost::getUserId, userId)
                .eq(CoffeePost::getStatus, 1));
        List<Long> postIds = posts.stream().map(CoffeePost::getId).collect(Collectors.toList());
        int impressions = 0;
        int clicks = 0;
        long dwellMs = 0L;
        int reports = 0;
        if (!postIds.isEmpty()) {
            List<FeedEvent> events = feedEventDao.selectList(new LambdaQueryWrapper<FeedEvent>()
                    .in(FeedEvent::getPostId, postIds));
            impressions = (int) events.stream().filter(e -> EVENT_IMPRESSION.equals(e.getEventType())).count();
            clicks = (int) events.stream().filter(e -> EVENT_CLICK.equals(e.getEventType())).count();
            dwellMs = events.stream()
                    .filter(e -> EVENT_DWELL.equals(e.getEventType()))
                    .map(FeedEvent::getDwellMs)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .sum();
            reports = postReportDao.selectCount(new LambdaQueryWrapper<PostReport>()
                    .in(PostReport::getPostId, postIds)).intValue();
        }
        return CreatorStatsVO.builder()
                .postCount(posts.size())
                .totalLikes(posts.stream().map(CoffeePost::getLikeCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum())
                .totalFavorites(posts.stream().map(CoffeePost::getFavoriteCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum())
                .totalComments(posts.stream().map(CoffeePost::getCommentCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum())
                .totalImpressions(impressions)
                .totalClicks(clicks)
                .totalDwellSeconds((int) (dwellMs / 1000))
                .totalReports(reports)
                .build();
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
            addPostToCollection(ensureDefaultCollection(userId).getId(), postId);

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
            removePostFromAllCollections(userId, postId);
            CoffeePost post = postDao.selectById(postId);
            post.setFavoriteCount(Math.max(0, post.getFavoriteCount() - 1));
            postDao.updateById(post);
            return false;
        }
    }

    public List<FavoriteCollectionVO> listMyCollections() {
        Long userId = SecurityUtils.requireUserId();
        ensureDefaultCollection(userId);
        return favoriteCollectionDao.selectList(new LambdaQueryWrapper<FavoriteCollection>()
                        .eq(FavoriteCollection::getUserId, userId)
                        .eq(FavoriteCollection::getStatus, 1)
                        .orderByDesc(FavoriteCollection::getIsDefault)
                        .orderByDesc(FavoriteCollection::getUpdateTime))
                .stream()
                .map(this::toCollectionVO)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public FavoriteCollectionVO createCollection(FavoriteCollectionForm form) {
        Long userId = SecurityUtils.requireUserId();
        FavoriteCollection collection = new FavoriteCollection();
        collection.setUserId(userId);
        collection.setName(form.getName().trim());
        collection.setDescription(form.getDescription());
        collection.setItemCount(0);
        collection.setIsDefault(0);
        collection.setStatus(1);
        collection.setCreateTime(LocalDateTime.now());
        collection.setUpdateTime(LocalDateTime.now());
        favoriteCollectionDao.insert(collection);
        return toCollectionVO(collection);
    }

    @Transactional(rollbackFor = Exception.class)
    public FavoriteCollectionVO updateCollection(Long collectionId, FavoriteCollectionForm form) {
        Long userId = SecurityUtils.requireUserId();
        FavoriteCollection collection = requireCollection(userId, collectionId);
        collection.setName(form.getName().trim());
        collection.setDescription(form.getDescription());
        collection.setUpdateTime(LocalDateTime.now());
        favoriteCollectionDao.updateById(collection);
        return toCollectionVO(collection);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCollection(Long collectionId) {
        Long userId = SecurityUtils.requireUserId();
        FavoriteCollection collection = requireCollection(userId, collectionId);
        if (Objects.equals(collection.getIsDefault(), 1)) {
            throw new ServiceException("默认收藏夹不能删除");
        }
        collection.setStatus(0);
        collection.setUpdateTime(LocalDateTime.now());
        favoriteCollectionDao.updateById(collection);
        favoriteCollectionItemDao.delete(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getCollectionId, collectionId)
                .eq(FavoriteCollectionItem::getUserId, userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void addPostToCollection(Long collectionId, Long postId) {
        Long userId = SecurityUtils.requireUserId();
        addPostToCollectionInternal(userId, collectionId, postId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removePostFromCollection(Long collectionId, Long postId) {
        Long userId = SecurityUtils.requireUserId();
        FavoriteCollection collection = requireCollection(userId, collectionId);
        int deleted = favoriteCollectionItemDao.delete(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getCollectionId, collectionId)
                .eq(FavoriteCollectionItem::getUserId, userId)
                .eq(FavoriteCollectionItem::getPostId, postId));
        if (deleted > 0) {
            collection.setItemCount(Math.max(0, collection.getItemCount() - deleted));
            collection.setUpdateTime(LocalDateTime.now());
            favoriteCollectionDao.updateById(collection);
        }
    }

    public List<PostListVO> listCollectionPosts(Long collectionId, Integer page, Integer size) {
        Long userId = SecurityUtils.requireUserId();
        requireCollection(userId, collectionId);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        List<Long> postIds = favoriteCollectionItemDao.selectList(new LambdaQueryWrapper<FavoriteCollectionItem>()
                        .eq(FavoriteCollectionItem::getCollectionId, collectionId)
                        .eq(FavoriteCollectionItem::getUserId, userId)
                        .orderByDesc(FavoriteCollectionItem::getCreateTime))
                .stream()
                .map(FavoriteCollectionItem::getPostId)
                .collect(Collectors.toList());
        if (postIds.isEmpty()) return List.of();
        int start = (safePage - 1) * safeSize;
        if (start >= postIds.size()) return List.of();
        List<Long> pageIds = postIds.subList(start, Math.min(start + safeSize, postIds.size()));
        return buildPostListVO(postDao.selectList(new LambdaQueryWrapper<CoffeePost>()
                .in(CoffeePost::getId, pageIds)
                .eq(CoffeePost::getStatus, 1)));
    }

    private FavoriteCollection ensureDefaultCollection(Long userId) {
        FavoriteCollection collection = favoriteCollectionDao.selectOne(new LambdaQueryWrapper<FavoriteCollection>()
                .eq(FavoriteCollection::getUserId, userId)
                .eq(FavoriteCollection::getIsDefault, 1)
                .last("LIMIT 1"));
        if (collection != null) return collection;
        collection = new FavoriteCollection();
        collection.setUserId(userId);
        collection.setName("默认收藏夹");
        collection.setDescription("系统自动创建的默认收藏夹");
        collection.setItemCount(0);
        collection.setIsDefault(1);
        collection.setStatus(1);
        collection.setCreateTime(LocalDateTime.now());
        collection.setUpdateTime(LocalDateTime.now());
        favoriteCollectionDao.insert(collection);
        return collection;
    }

    private FavoriteCollection requireCollection(Long userId, Long collectionId) {
        FavoriteCollection collection = favoriteCollectionDao.selectById(collectionId);
        if (collection == null || !Objects.equals(collection.getUserId(), userId) || Objects.equals(collection.getStatus(), 0)) {
            throw new ServiceException("收藏夹不存在");
        }
        return collection;
    }

    private void addPostToCollectionInternal(Long userId, Long collectionId, Long postId) {
        CoffeePost post = postDao.selectById(postId);
        if (post == null || Objects.equals(post.getStatus(), 0)) {
            throw new ServiceException("帖子不存在");
        }
        FavoriteCollection collection = requireCollection(userId, collectionId);
        Long count = favoriteCollectionItemDao.selectCount(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getCollectionId, collectionId)
                .eq(FavoriteCollectionItem::getUserId, userId)
                .eq(FavoriteCollectionItem::getPostId, postId));
        if (count > 0) return;
        FavoriteCollectionItem item = new FavoriteCollectionItem();
        item.setCollectionId(collectionId);
        item.setUserId(userId);
        item.setPostId(postId);
        item.setCreateTime(LocalDateTime.now());
        favoriteCollectionItemDao.insert(item);
        collection.setItemCount(collection.getItemCount() + 1);
        collection.setUpdateTime(LocalDateTime.now());
        favoriteCollectionDao.updateById(collection);
    }

    private void removePostFromAllCollections(Long userId, Long postId) {
        List<FavoriteCollectionItem> items = favoriteCollectionItemDao.selectList(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getUserId, userId)
                .eq(FavoriteCollectionItem::getPostId, postId));
        if (items.isEmpty()) return;
        favoriteCollectionItemDao.delete(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getUserId, userId)
                .eq(FavoriteCollectionItem::getPostId, postId));
        Map<Long, Long> counts = items.stream()
                .collect(Collectors.groupingBy(FavoriteCollectionItem::getCollectionId, Collectors.counting()));
        counts.forEach((collectionId, count) -> {
            FavoriteCollection collection = favoriteCollectionDao.selectById(collectionId);
            if (collection != null) {
                collection.setItemCount(Math.max(0, collection.getItemCount() - count.intValue()));
                collection.setUpdateTime(LocalDateTime.now());
                favoriteCollectionDao.updateById(collection);
            }
        });
    }

    private FavoriteCollectionVO toCollectionVO(FavoriteCollection collection) {
        return FavoriteCollectionVO.builder()
                .id(collection.getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .itemCount(collection.getItemCount())
                .defaultCollection(Objects.equals(collection.getIsDefault(), 1))
                .updateTime(collection.getUpdateTime())
                .build();
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
        if (from.getParentId() != null) {
            CoffeeComment parent = commentDao.selectById(from.getParentId());
            if (parent == null || !Objects.equals(parent.getPostId(), postId)) {
                throw new ServiceException("父评论不存在");
            }
            comment.setParentId(parent.getId());
            comment.setRootId(parent.getRootId() == null ? parent.getId() : parent.getRootId());
            comment.setReplyToUserId(from.getReplyToUserId() == null ? parent.getUserId() : from.getReplyToUserId());
        }
        comment.setContent(from.getContent());
        comment.setLikeCount(0);
        commentDao.insert(comment);

        post.setCommentCount(post.getCommentCount() + 1);
        postDao.updateById(post);

        UserFeignClient.UserInfo userInfo = batchGetUserInfo(List.of(userId)).get(userId);
        String username = userInfo != null && userInfo.username() != null ? userInfo.username() : "未知";
        String avatar = userInfo != null ? userInfo.avatar() : null;

        // TODO: 发送通知到 wc-message

        return CommentVO.builder()
                .id(comment.getId())
                .userId(userId)
                .parentId(comment.getParentId())
                .rootId(comment.getRootId())
                .replyToUserId(comment.getReplyToUserId())
                .username(username)
                .avatar(avatar)
                .content(from.getContent())
                .likeCount(comment.getLikeCount())
                .replies(List.of())
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
        detachPostTopics(id);
        postProductDao.delete(new LambdaQueryWrapper<PostProduct>().eq(PostProduct::getPostId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePost(Long id, PostCreateFrom from) {
        Long userId = SecurityUtils.requireUserId();

        CoffeePost post = postDao.selectById(id);
        if (post == null) throw new ServiceException("帖子不存在");
        if (!post.getUserId().equals(userId)) throw new ServiceException("这不是你发布的帖子");

        post.setTitle(from.getTitle());
        post.setContent(from.getContent());
        post.setNoteType(normalizeNoteType(from.getNoteType()));
        post.setVideoUrl(from.getVideoUrl());
        post.setCoverUrl(from.getCoverUrl());
        post.setVideoDuration(from.getVideoDuration());
        post.setCoffeeName(from.getCoffeeName());
        post.setCoffeeBrand(from.getCoffeeBrand());
        post.setLocation(from.getLocation());
        if (from.getImages() != null && !from.getImages().isEmpty()) {
            post.setImages(JSONUtil.toJsonStr(from.getImages()));
        }

        postDao.updateById(post);
        replacePostTopics(id, from.getTopics());
        replacePostProducts(id, from.getProductIds());
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

        List<Long> deleteIds = commentDao.selectList(new LambdaQueryWrapper<CoffeeComment>()
                        .eq(CoffeeComment::getRootId, commentId))
                .stream()
                .map(CoffeeComment::getId)
                .collect(Collectors.toCollection(ArrayList::new));
        deleteIds.add(commentId);
        deleteIds = deleteIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        commentDao.deleteBatchIds(deleteIds);
        post.setCommentCount(Math.max(0, post.getCommentCount() - deleteIds.size()));
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
        return fileStorageService.upload(file, "uploads/" + fileName);
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
