package cn.lx.worldcoffee.user.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.result.Constant;
import cn.lx.worldcoffee.common.security.JwtUtil;
import cn.lx.worldcoffee.common.security.SecurityUtils;
import cn.lx.worldcoffee.common.storage.FileStorageService;
import cn.lx.worldcoffee.user.dao.*;
import cn.lx.worldcoffee.user.domain.*;
import cn.lx.worldcoffee.user.domain.from.*;
import cn.lx.worldcoffee.user.domain.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserDao userDao;
    private final UserFollowDao followDao;
    private final CoffeePostDao postDao;
    private final CoffeeLikeDao likeDao;
    private final CoffeeFavoriteDao favoriteDao;
    private final CoffeeCommentDao commentDao;
    private final SmsService smsService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final FileStorageService fileStorageService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterForm form) {
        Long usernameCount = userDao.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, form.getUsername()));
        if (usernameCount > 0) throw new ServiceException("用户名已被注册");

        if (form.getPhone() != null && !form.getPhone().isBlank()) {
            Long phoneCount = userDao.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, form.getPhone()));
            if (phoneCount > 0) throw new ServiceException("手机号已注册");
        }

        User user = new User();
        user.setUsername(form.getUsername());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setPhone(form.getPhone());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userDao.insert(user);

        cacheUser(user);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    public LoginVO login(LoginFrom form) {
        User user = userDao.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, form.getUsername()));
        if (user == null) throw new ServiceException("用户名或密码错误");
        if (!passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            throw new ServiceException("用户名或密码错误");
        }
        if (Objects.equals(user.getStatus(), 0)) {
            throw new ServiceException("账号已被冻结或注销");
        }

        cacheUser(user);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    public ReturnMeVO getMe() {
        Long userId = SecurityUtils.requireUserId();
        User user = getUserById(userId);
        return ReturnMeVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .status(user.getStatus())
                .avatar(user.getAvatar())
                .createTime(user.getCreateTime())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(UpdateProfileFrom form) {
        Long userId = SecurityUtils.requireUserId();
        User user = getUserById(userId);

        if (form.getUsername() != null && !form.getUsername().isBlank()
                && !Objects.equals(user.getUsername(), form.getUsername())) {
            Long count = userDao.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, form.getUsername())
                    .ne(User::getId, userId));
            if (count > 0) throw new ServiceException("用户名已被使用");
            user.setUsername(form.getUsername());
        }

        if (form.getPhone() != null && !form.getPhone().isBlank()
                && !Objects.equals(user.getPhone(), form.getPhone())) {
            Long count = userDao.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, form.getPhone())
                    .ne(User::getId, userId));
            if (count > 0) throw new ServiceException("手机号已被占用");
            user.setPhone(form.getPhone());
        }

        if (form.getAvatar() != null) user.setAvatar(form.getAvatar());
        userDao.updateById(user);
        clearUserCache(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordFrom form) {
        Long userId = SecurityUtils.requireUserId();
        User user = getUserById(userId);
        if (!passwordEncoder.matches(form.getOldPassword(), user.getPassword())) {
            throw new ServiceException("旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        userDao.updateById(user);
        clearUserCache(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindPhone(BindPhoneFrom form) {
        Long userId = SecurityUtils.requireUserId();
        if (!smsService.verifySmsCode(form.getPhone(), form.getCode())) {
            throw new ServiceException("验证码错误或已过期");
        }
        Long count = userDao.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, form.getPhone())
                .ne(User::getId, userId));
        if (count > 0) throw new ServiceException("手机号已被占用");

        User user = getUserById(userId);
        user.setPhone(form.getPhone());
        userDao.updateById(user);
        clearUserCache(userId);
    }

    public String sendSmsCode(String phone) {
        return smsService.sendSmsCode(phone);
    }

    public UserProfileVO getUserProfile(Long userId) {
        User user = getUserById(userId);
        Long currentUserId = SecurityUtils.getCurrentUserId();

        Long postCount = postDao.selectCount(new LambdaQueryWrapper<CoffeePost>()
                .eq(CoffeePost::getUserId, userId)
                .eq(CoffeePost::getStatus, 1));
        Long followingCount = followDao.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId));
        Long followerCount = followDao.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, userId));

        boolean isFollowing = currentUserId != null && followDao.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFolloweeId, userId)) > 0;

        List<CoffeePost> recentPosts = postDao.selectList(new LambdaQueryWrapper<CoffeePost>()
                .eq(CoffeePost::getUserId, userId)
                .eq(CoffeePost::getStatus, 1)
                .orderByDesc(CoffeePost::getCreateTime)
                .last("LIMIT 10"));

        return UserProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .postCount(postCount.intValue())
                .followingCount(followingCount.intValue())
                .followerCount(followerCount.intValue())
                .isFollowing(isFollowing)
                .createTime(user.getCreateTime())
                .recentPosts(buildUserPostVO(recentPosts, user))
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean toggleFollow(Long followeeId) {
        Long userId = SecurityUtils.requireUserId();
        if (userId.equals(followeeId)) throw new ServiceException("不能关注自己");
        getUserById(followeeId);

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getFolloweeId, followeeId);
        Long count = followDao.selectCount(wrapper);
        if (count > 0) {
            followDao.delete(wrapper);
            return false;
        }

        UserFollow follow = new UserFollow();
        follow.setFollowerId(userId);
        follow.setFolloweeId(followeeId);
        follow.setCreateTime(LocalDateTime.now());
        followDao.insert(follow);
        return true;
    }

    public List<FollowingVO> getFollowingList(Long userId, int page, int size) {
        List<Long> followeeIds = followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
                .orderByDesc(UserFollow::getCreateTime)
                .last(limit(page, size)))
                .stream().map(UserFollow::getFolloweeId).collect(Collectors.toList());
        return buildFollowingVO(followeeIds);
    }

    public List<FollowingVO> getFollowersList(Long userId, int page, int size) {
        List<Long> followerIds = followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, userId)
                .orderByDesc(UserFollow::getCreateTime)
                .last(limit(page, size)))
                .stream().map(UserFollow::getFollowerId).collect(Collectors.toList());
        return buildFollowingVO(followerIds);
    }

    public List<FollowingVO> searchUsers(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) return List.of();
        List<Long> ids = userDao.selectList(new LambdaQueryWrapper<User>()
                .like(User::getUsername, keyword)
                .eq(User::getStatus, 1)
                .last(limit(page, size)))
                .stream().map(User::getId).collect(Collectors.toList());
        return buildFollowingVO(ids);
    }

    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(MultipartFile file) {
        Long userId = SecurityUtils.requireUserId();
        if (file == null || file.isEmpty()) throw new ServiceException("文件不能为空");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ServiceException("只能上传图片");
        }

        String originalName = file.getOriginalFilename();
        String suffix = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : ".png";
        String fileName = "avatar_" + userId + "_" + System.currentTimeMillis() + suffix;

        String avatarUrl = fileStorageService.upload(file, "uploads/avatars/" + fileName);
        User user = getUserById(userId);
        user.setAvatar(avatarUrl);
        userDao.updateById(user);
        clearUserCache(userId);
        return avatarUrl;
    }

    public void logout(String authHeader) {
        String token = extractBearer(authHeader);
        if (token == null) return;
        Long userId = parseUserId(token);
        if (userId != null) clearUserCache(userId);
        stringRedisTemplate.opsForSet().add("token:blacklist", token);
        stringRedisTemplate.expire("token:blacklist", Constant.JWT_EXPIRATION, TimeUnit.MILLISECONDS);
    }

    public UserStatsVO getMyStats() {
        Long userId = SecurityUtils.requireUserId();
        List<CoffeePost> visiblePosts = postDao.selectList(new LambdaQueryWrapper<CoffeePost>()
                .select(CoffeePost::getId,
                        CoffeePost::getLikeCount,
                        CoffeePost::getFavoriteCount,
                        CoffeePost::getCommentCount)
                .eq(CoffeePost::getUserId, userId)
                .eq(CoffeePost::getStatus, 1));

        Long postCount = (long) visiblePosts.size();
        Long likeCount = visiblePosts.stream()
                .mapToLong(post -> post.getLikeCount() == null ? 0L : post.getLikeCount())
                .sum();
        Long favoriteCount = visiblePosts.stream()
                .mapToLong(post -> post.getFavoriteCount() == null ? 0L : post.getFavoriteCount())
                .sum();
        Long commentCount = visiblePosts.stream()
                .mapToLong(post -> post.getCommentCount() == null ? 0L : post.getCommentCount())
                .sum();

        Long followingCount = followDao.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId));
        Long followerCount = followDao.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, userId));

        return UserStatsVO.builder()
                .postCount(postCount)
                .likeCount(likeCount)
                .favoriteCount(favoriteCount)
                .commentCount(commentCount)
                .followingCount(followingCount)
                .followerCount(followerCount)
                .build();
    }

    public LoginVO refreshToken(String authHeader) {
        String token = extractBearer(authHeader);
        if (token == null) throw new ServiceException("token格式错误");
        Boolean blacklisted = stringRedisTemplate.opsForSet().isMember("token:blacklist", token);
        if (Boolean.TRUE.equals(blacklisted)) {
            throw new ServiceException(401, "Token已失效，请重新登录");
        }

        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new ServiceException(401, "token无效或已过期，请重新登录");
        }

        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String newToken = jwtUtil.generateToken(userId, username);
        stringRedisTemplate.expire("user:info" + userId, Constant.JWT_EXPIRATION, TimeUnit.MILLISECONDS);
        return LoginVO.builder()
                .token(newToken)
                .userId(userId)
                .username(username)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(String authHeader) {
        Long userId = SecurityUtils.requireUserId();
        User user = getUserById(userId);
        user.setStatus(0);
        userDao.updateById(user);
        logout(authHeader);
    }

    public Map<Long, UserInfo> batchGetUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<User> users = userDao.selectBatchIds(ids);
        return users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> new UserInfo(u.getId(), u.getUsername(), u.getAvatar())
                ));
    }

    private User getUserById(Long userId) {
        User user = userDao.selectById(userId);
        if (user == null || Objects.equals(user.getStatus(), 0)) {
            throw new ServiceException("用户不存在");
        }
        return user;
    }

    private List<FollowingVO> buildFollowingVO(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Map<Long, User> userMap = userDao.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Set<Long> myFollowees = getMyFollowees(ids);
        return ids.stream().map(id -> {
            User user = userMap.get(id);
            return FollowingVO.builder()
                    .id(id)
                    .username(user != null ? user.getUsername() : "未知用户")
                    .avatar(user != null ? user.getAvatar() : null)
                    .isFollowing(myFollowees.contains(id))
                    .build();
        }).collect(Collectors.toList());
    }

    private Set<Long> getMyFollowees(List<Long> ids) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null || ids == null || ids.isEmpty()) return Set.of();
        return followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUserId)
                .in(UserFollow::getFolloweeId, ids))
                .stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());
    }

    private List<UserPostVO> buildUserPostVO(List<CoffeePost> posts, User user) {
        if (posts == null || posts.isEmpty()) return List.of();
        return posts.stream().map(post -> UserPostVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .title(post.getTitle())
                .content(post.getContent())
                .images(parseImages(post.getImages()))
                .coffeeName(post.getCoffeeName())
                .coffeeBrand(post.getCoffeeBrand())
                .location(post.getLocation())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .favoriteCount(post.getFavoriteCount())
                .createTime(post.getCreateTime())
                .build()).collect(Collectors.toList());
    }

    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return List.of();
        try {
            return JSONUtil.toList(imagesJson, String.class);
        } catch (Exception e) {
            if (imagesJson.contains(",")) {
                return Arrays.stream(imagesJson.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
            return List.of(imagesJson);
        }
    }

    private String limit(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(Math.min(size, 100), 1);
        return "LIMIT " + ((safePage - 1) * safeSize) + "," + safeSize;
    }

    private void cacheUser(User user) {
        stringRedisTemplate.opsForValue().set("user:info" + user.getId(),
                JSONUtil.toJsonStr(user), Constant.JWT_EXPIRATION, TimeUnit.MILLISECONDS);
    }

    private void clearUserCache(Long userId) {
        stringRedisTemplate.delete("user:info" + userId);
    }

    private String extractBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7);
    }

    private Long parseUserId(String token) {
        try {
            return Long.valueOf(jwtUtil.parseToken(token).getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    public record UserInfo(Long id, String username, String avatar) {}
}
