package cn.lx.worldcoffee.user.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.common.security.JwtUtil;
import cn.lx.worldcoffee.user.config.WxMiniAppProperties;
import cn.lx.worldcoffee.user.dao.UserDao;
import cn.lx.worldcoffee.user.domain.User;
import cn.lx.worldcoffee.user.domain.vo.LoginVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 微信小程序一键登录：wx.login code → openid → 绑定/建档 → 发 JWT。
 *
 * 为什么单独建绑定表而不是给 sys_user 加 openid 列：
 *   本仓库没有统一 schema 迁移管理（各模块 SQL 散落在 resources/db），
 *   加列需要人工 ALTER 每台环境的库；绑定表用 CREATE TABLE IF NOT EXISTS
 *   在服务启动时自愈建表，新旧环境零人工干预。
 *
 * 开发期（wechat.miniapp.mock-enabled=true）不调微信服务器，用固定 openid
 * 打通全链路；生产置 false 并配置 appid/app-secret 即切换为真实 code2Session。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WxLoginService {

    private static final String MOCK_OPENID = "mock_openid_dev";

    private final WxMiniAppProperties props;
    private final UserDao userDao;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    void initBindTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS wx_user_bind (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id     BIGINT       NOT NULL,
                    openid      VARCHAR(64)  NOT NULL,
                    create_time DATETIME     NULL,
                    UNIQUE KEY uk_wx_openid (openid),
                    KEY idx_wx_user (user_id)
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
                """);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(String code) {
        String openid = resolveOpenid(code);

        Long boundUserId = jdbcTemplate.query(
                "SELECT user_id FROM wx_user_bind WHERE openid = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                openid);
        User user = boundUserId == null ? null : userDao.selectById(boundUserId);
        if (user == null) {
            user = createUser(openid);
        }
        if (Objects.equals(user.getStatus(), 0)) {
            throw new ServiceException("账号已被冻结或注销");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    /**
     * code 换 openid。mock 模式返回固定值，保证本地反复登录绑定同一账号。
     */
    private String resolveOpenid(String code) {
        if (props.isMockEnabled()) {
            log.info("[WxLogin] mock 模式登录，code={} 已忽略", code);
            return MOCK_OPENID;
        }
        if (StrUtil.hasBlank(props.getAppid(), props.getAppSecret())) {
            throw new ServiceException("服务端未配置微信小程序密钥，微信登录不可用");
        }
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                props.getAppid(), props.getAppSecret(), code);
        String body = HttpUtil.get(url, 5000);
        JSONObject json = JSONUtil.parseObj(body);
        String openid = json.getStr("openid");
        if (StrUtil.isBlank(openid)) {
            throw new ServiceException("微信登录失败：" + json.getStr("errmsg", "unknown"));
        }
        return openid;
    }

    /**
     * 首次登录自动建档：用户名 wx_xxxxxx，密码为随机串（微信用户不走密码登录）。
     */
    private User createUser(String openid) {
        User user = new User();
        user.setUsername(uniqueUsername());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userDao.insert(user);

        jdbcTemplate.update(
                "INSERT INTO wx_user_bind (user_id, openid, create_time) VALUES (?, ?, ?)",
                user.getId(), openid, LocalDateTime.now());
        log.info("[WxLogin] 新建微信绑定用户 userId={} openid={}", user.getId(), openid);
        return user;
    }

    private String uniqueUsername() {
        for (int i = 0; i < 5; i++) {
            String name = "wx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            Long count = userDao.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getUsername, name));
            if (count == 0) return name;
        }
        throw new ServiceException("微信账号建档失败，请重试");
    }
}
