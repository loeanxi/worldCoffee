package cn.lx.worldcoffee.module.user.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.common.security.JwtUtil;
import cn.lx.worldcoffee.module.coffee.dao.CoffeePostDao;
import cn.lx.worldcoffee.module.coffee.dao.UserFollowDao;
import cn.lx.worldcoffee.module.coffee.domain.CoffeePost;
import cn.lx.worldcoffee.module.coffee.domain.UserFollow;
import cn.lx.worldcoffee.module.coffee.service.CoffeeService;
import cn.lx.worldcoffee.module.notification.domain.NotificationEvent;
import cn.lx.worldcoffee.module.notification.service.NotificationService;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import cn.lx.worldcoffee.module.user.domain.form.ChangePasswordFrom;
import cn.lx.worldcoffee.module.user.domain.form.LoginFrom;
import cn.lx.worldcoffee.module.user.domain.form.RegisterForm;
import cn.lx.worldcoffee.module.user.domain.form.UpdateProfileFrom;
import cn.lx.worldcoffee.module.user.domain.vo.FollowingVO;
import cn.lx.worldcoffee.module.user.domain.vo.LoginVO;
import cn.lx.worldcoffee.module.user.domain.vo.ReturnMeVO;
import cn.lx.worldcoffee.module.user.domain.vo.UserProfileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final CoffeePostDao postDao;
    private final CoffeeService coffeeService;
    private final UserFollowDao followDao;
    private final NotificationService notificationService;

    /** 获取当前登录用户ID，未登录返回null */
    public Long getCurrentUserId(){
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null){
                return Long.valueOf(auth.getPrincipal().toString());
            }
        }catch (Exception ignored){}
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterForm form) {
        // 1. 校验用户名是否重复
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, form.getUsername());
        if (userDao.selectCount(wrapper) > 0) {
            throw new RuntimeException("用户名重复");
        }
        // 2. 手机号唯一校验
        wrapper.clear();
        wrapper.eq(User::getPhone, form.getPhone());
        if (userDao.selectCount(wrapper) > 0) {
            throw new RuntimeException("手机号已注册");
        }
        // 3. 密码加密
        String encode = passwordEncoder.encode(form.getPassword());
        // 4. 组装用户数据入库
        User user = new User();
        user.setUsername(form.getUsername());
        user.setPassword(encode);
        user.setPhone(form.getPhone());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userDao.insert(user);
        // 5. 生成JWT
        String token = jwtUtil.generateToken(user.getId().toString(), user.getUsername());

        // ===== 新增：用户信息存Redis =====
        // 去掉密码，只存安全字段
        //hutool序列化
        JSONObject obj = JSONUtil.parseObj(user);
        obj.remove("password");
        String userJson = obj.toString();
        //反序列为user对象
        User user1 = JSONUtil.toBean(userJson, User.class);
        stringRedisTemplate.opsForValue().set
                ("user:info" + user.getId(), userJson,86400, TimeUnit.SECONDS);//24小时

        // 6. 返回登录VO
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    public LoginVO login(LoginFrom from) {

        // 1. 根据用户名查询用户
        User user = userDao.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, from.getUsername()));
        // 2. 用户不存在
        if (user == null){
            throw new RuntimeException("用户名不存在");
        }
        // 3. 校验密码
        if (!passwordEncoder.matches(from.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        // 4. 校验账号状态
        if (user.getStatus() == 0){
            throw new RuntimeException("您的账号被冻结 请联系管理员");
        }
        // 5. 生成JWT令牌
        String token = jwtUtil.generateToken(user.getId().toString(), user.getUsername());

        //序列化user对象为json对象 再转为json字符串
        JSONObject obj = JSONUtil.parseObj(user);
        obj.remove("password");
        String userJson = obj.toString();

        stringRedisTemplate.opsForValue()
                .set("user:info" + user.getId(),userJson,86400,TimeUnit.SECONDS);

        // 6. 返回登录信息
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    public ReturnMeVO ReturnMe() {
        // 1. 从 Spring Security 登记簿里拿当前登录用户的 userId
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = Long.valueOf(auth.getPrincipal().toString());


        // ===== 新增：优先从Redis取 =====
        String userJson = stringRedisTemplate.opsForValue().get("user:info" + userId);

        if (userJson != null) {
            // Redis命中，直接解析返回（不查库）
            // 这里用简单字符串解析，也可以用hutool的JSONUtil
            User user = JSONUtil.toBean(userJson, User.class);
            return ReturnMeVO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .phone(user.getPhone())
                    .status(user.getStatus())
                    .createTime(user.getCreateTime())
                    .build();
        }

        //redis没命中 查库降级
        // 2. 用 userId 查数据库
        User user = userDao.selectById(userId);
        if (user == null){
            throw new RuntimeException("用户不存在");
        }
        //查到后顺便缓存到redis
        JSONObject obj = JSONUtil.parseObj(user);
        obj.remove("password");
        String userJson1 = obj.toString();

        stringRedisTemplate.opsForValue().set("user:info" + userId,userJson1,86400,TimeUnit.SECONDS);


        // 3. Entity → VO（丢掉 password，只返回安全字段）
        return ReturnMeVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .avatar(user.getAvatar())
                .build();
    }

    public void updateProfile(UpdateProfileFrom from) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请xian登录qaq");

        // 1. 查当前用户
        // SQL: SELECT * FROM sys_user WHERE id = ?
        User user = userDao.selectById(userId);

        // 2. 改用户名了 → 校验新用户名有没有被别人占用
        // SQL: SELECT COUNT(*) FROM sys_user WHERE username = ? AND id != ?
        if (!user.getUsername().equals(from.getUsername())){
            Long count = userDao.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, from.getUsername())
                    .ne(User::getId, userId)
            );
            if (count > 0) throw new RuntimeException("用户名已被使用");
        }
        // 3. 改手机号了 → 校验新手机号有没有被别人占用
        // SQL: SELECT COUNT(*) FROM sys_user WHERE phone = ? AND id != ?
        if (!user.getPhone().equals(from.getPhone())) {
            Long count = userDao.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getPhone, from.getPhone())
                            .ne(User::getId, userId)
            );
            if (count > 0) throw new RuntimeException("手机号已被占用");
        }
        // 4. 更新
        // SQL: UPDATE sys_user SET username = ?, phone = ? WHERE id = ?
        user.setUsername(from.getUsername());
        user.setPhone(from.getPhone());
        user.setAvatar(from.getAvatar());
        userDao.updateById(user);

        //关键点：校验唯一性时用 .ne(User::getId, userId) 排除自己。否则用户不改用户名只改手机号，校验用户名时会把自己也算成冲突。
        //AND id != ? 就是把"我自己"从查重范围里踢出去，否则改任何字段时自己的名字都会触发冲突。


        /**
         * 张三要改名叫"李四"
         * 数据库现状：
         *   id=3  username="张三"   ← 当前用户
         *   id=5  username="李四"   ← 已经存在的另一个人
         *
         * 张三提交：username = "李四"
         *
         * 你的写法：count >= 2
         * SELECT COUNT(*) FROM sys_user WHERE username = '李四'
         * -- 结果：1（只有 id=5 的李四，张三现在还是叫"张三"，不算）
         *
         * count = 1
         * 1 >= 2  →  false
         * → 没抛异常 → 允许修改 ✅❓ → 改完数据库里出现两个"李四"！
         *
         *
         * 我的写法：count > 0 + .ne(User::getId, userId)
         * SELECT COUNT(*) FROM sys_user WHERE username = '李四' AND id != 3
         * -- 结果：1（id=5 的李四存在，而且不是我自己）
         *
         * count = 1
         * 1 > 0  →  true
         * → 抛异常 "用户名已被占用" ✅
         *
         * 关键：张三改名叫"李四"时，他自己还叫"张三"，不叫"李四"。所以查"李四"只会查到一个（id=5那个），
         * count 永远是 1，>= 2 永远不触发。.ne(User::getId, userId)
         * 不是为了减掉自己（因为本来就不算自己），
         * 而是万一以后代码被人改了——比如说有人把前面那个 if 删掉了，.ne() 能兜底。
         */
    }

    public UserProfileVO getUserFile(Long userId) {
        // SQL: SELECT * FROM sys_user WHERE id = ?
        User user = userDao.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        // SQL: SELECT COUNT(*) FROM coffee_post
        // WHERE user_id = ? AND status = 1
        Long postCount = postDao.selectCount(new LambdaQueryWrapper<CoffeePost>()
                .eq(CoffeePost::getUserId, userId)
                .eq(CoffeePost::getStatus, 1)
        );
        // SQL: SELECT * FROM coffee_post
        // WHERE user_id = ? AND status = 1 ORDER BY create_time DESC LIMIT 10
        List<CoffeePost> posts = postDao.selectList(
                new LambdaQueryWrapper<CoffeePost>()
                        .eq(CoffeePost::getUserId, userId)
                        .eq(CoffeePost::getStatus, 1)
                        .orderByDesc(CoffeePost::getCreateTime)
                        .last("LIMIT 10")
        );

        Long currentUserId = getCurrentUserId();
        //select count(*) from user_follow where follower_id = ? and followee_id = ?
        boolean isFollowing = currentUserId != null && followDao.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId,currentUserId)
                .eq(UserFollow::getFolloweeId,userId)
        ) > 0;

        return UserProfileVO.builder()
                .id(userId)
                .username(user.getUsername())
                .postCount(postCount.intValue())
                .createTime(user.getCreateTime())
                .recentPosts(coffeeService.buildPostListVO(posts))
                .isFollowing(isFollowing)
                .avatar(user.getAvatar())
                .build();
    }

    public void changePassword(ChangePasswordFrom from) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");

        // 1. 查当前用户（拿到加密后的旧密码）
        // SQL: SELECT * FROM sys_user WHERE id = ?
        User user = userDao.selectById(userId);

        // 2. 校验旧密码是否正确
        // 用 BCryptPasswordEncoder 的 matches 方法，不是 SQL 比对的
        if (!passwordEncoder.matches(from.getOldPassword(), user.getPassword())){
            throw new RuntimeException("旧密码错误");
        }
        // 3. 新密码加密
        String newEncoded = passwordEncoder.encode(from.getNewPassword());

        // 4. 更新
        // SQL: UPDATE sys_user SET password = ? WHERE id = ?
        user.setPassword(newEncoded);
        //一句话：updateById 的名字里有 ById，这个 ById 说"WHERE 条件用 id"，
        // 但 SET 后面的值从你传的对象里拿。
        // 不是传 ID 进去让它更新，是传一个改好的对象进去让它按 ID 定位。
        userDao.updateById(user);
    }

    public Boolean toggleFollow(Long followeeId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");
        if (userId.equals(followeeId)) throw new RuntimeException("不能关注自己");

        // SQL: SELECT COUNT(*) FROM user_follow
        // WHERE follower_id = ? AND followee_id = ?
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId,userId)
                .eq(UserFollow::getFolloweeId,followeeId);
        Long count = followDao.selectCount(wrapper);

        if (count > 0) {
            // SQL: DELETE FROM user_follow WHERE follower_id = ? AND followee_id = ?
            followDao.delete(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, userId)
                            .eq(UserFollow::getFolloweeId, followeeId)
            );
            return false;
        } else {
            // SQL: INSERT INTO user_follow (follower_id, followee_id) VALUES (?, ?)
            UserFollow follow = new UserFollow();
            follow.setFollowerId(userId);
            follow.setFolloweeId(followeeId);
            followDao.insert(follow);
            notificationService.send(NotificationEvent.builder()
                    .receiverId(followeeId)
                    .senderId(userId)
                    .type("FOLLOW")
                    .content("关注了你")
                    .build());
            return true;
        }
    }

    public List<FollowingVO> getFollowingList(Long userId, int page, int size) {
        // 1. 查出目标用户关注的所有人的ID
        // SQL: SELECT followee_id FROM user_follow
        // WHERE follower_id = ? ORDER BY create_time DESC
        List<Long> followeeIds = followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
                .orderByAsc(UserFollow::getCreateTime)
                .last("LIMIT " + (page - 1) * size + "," + size)
        ).stream().map(UserFollow::getFolloweeId).collect(Collectors.toList());
        if (followeeIds.isEmpty()) return List.of();

        // 2. 批量查用户信息（修 N+1）
        Map<Long, User> userMap = userDao.selectBatchIds(followeeIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        // 3. 查当前用户关注了其中哪些人（用于 isFollowing 字段）
        Long currentUserId = getCurrentUserId();
        Set<Long> myFollwees = new HashSet<>();
        if (currentUserId != null){
            myFollwees = followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId,currentUserId)
                    .in(UserFollow::getFolloweeId,followeeIds)
            ).stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());
        }
        // 4. 组装 VO
        Set<Long> finalFollowees = myFollwees;
        return followeeIds.stream().map(id -> {
            User u = userMap.get(id);
            return FollowingVO.builder()
                    .id(id)
                    .username(u != null ? u.getUsername() : "未知")
                    .avatar(u != null ? u.getAvatar() : null)
                    .isFollowing(finalFollowees.contains(id))
                    .build();
        }).collect(Collectors.toList());
    }

    public List<FollowingVO> getFollowersList(Long userId, int page, int size) {
        // 1. 查出关注目标用户的所有人的ID
        // 和关注列表的区别：follower_id ↔ followee_id 对调
        // SQL: SELECT follower_id FROM user_follow
        // WHERE followee_id = ? ORDER BY create_time DESC
        List<Long> followerIds = followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, userId)
                .orderByDesc(UserFollow::getCreateTime)
        ).stream().map(UserFollow::getFollowerId).collect(Collectors.toList());
        if (followerIds.isEmpty()) return List.of();

        Map<Long, User> userMap = userDao.selectBatchIds(followerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Long currentUserId = getCurrentUserId();
        Set<Long> myFollowees = new HashSet<>();

        if (currentUserId != null){
            myFollowees = followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, currentUserId)
                    .in(UserFollow::getFolloweeId, followerIds)
            ).stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());
        }
        Set<Long> finalFollowees = myFollowees;
        return followerIds.stream().map(id -> {
            User u = userMap.get(id);
            return FollowingVO.builder()
                    .id(id)
                    .username(u != null ? u.getUsername() : "未知")
                    .avatar(u != null ? u.getAvatar() : null)
                    .isFollowing(finalFollowees.contains(id))
                    .build();
        }).collect(Collectors.toList());
    }

    public List<FollowingVO> searchUsers(String keyword, int page, int size) {
        // SQL: SELECT * FROM sys_user
        // WHERE username LIKE '%keyword%' AND status = 1 LIMIT ?,?
        List<User> users = userDao.selectList(new LambdaQueryWrapper<User>()
                .like(User::getUsername, keyword)
                .eq(User::getStatus, 1)
                .last("LIMIT " + (page - 1) * size + "," + size)
        );
        if (users.isEmpty()) return List.of();

        // 查当前用户关注了其中哪些人
        Long currentUserId = getCurrentUserId();
        Set<Long> myFollowees = new HashSet<>();
        if (currentUserId != null) {
            List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
            myFollowees = followDao.selectList(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, currentUserId)
                    .in(UserFollow::getFolloweeId, userIds)
            ).stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());

        }
        Set<Long> finalFollowees = myFollowees;
        return users.stream().map(u -> FollowingVO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .avatar(u.getAvatar())
                .isFollowing(finalFollowees.contains(u.getId()))
                .build()).collect(Collectors.toList());
    }
}
