package cn.lx.worldcoffee.module.user.service;

import cn.lx.worldcoffee.common.security.JwtUtil;
import cn.lx.worldcoffee.module.user.dao.UserDao;
import cn.lx.worldcoffee.module.user.domain.User;
import cn.lx.worldcoffee.module.user.domain.form.RegisterForm;
import cn.lx.worldcoffee.module.user.domain.form.LoginFrom;
import cn.lx.worldcoffee.module.user.domain.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock private UserDao userDao;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @Test
    void register_shouldSucceed() {
        when(userDao.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_pwd");
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("test_token");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        // 模拟 MyBatis-Plus insert 后自动设置 ID
        doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return 1;
        }).when(userDao).insert(any(User.class));

        RegisterForm form = new RegisterForm();
        form.setUsername("new_user");
        form.setPassword("123456");
        form.setPhone("13800138000");

        LoginVO vo = userService.register(form);
        assertNotNull(vo);
        assertEquals("test_token", vo.getToken());
        assertEquals("new_user", vo.getUsername());
    }

    @Test
    void register_shouldThrowWhenUsernameExists() {
        when(userDao.selectCount(any())).thenReturn(1L);

        RegisterForm form = new RegisterForm();
        form.setUsername("existing_user");
        form.setPassword("123456");
        form.setPhone("13800138000");

        assertThrows(RuntimeException.class, () -> userService.register(form));
    }

    @Test
    void login_shouldSucceed() {
        User user = new User();
        user.setId(1L);
        user.setUsername("test_user");
        user.setPassword("encoded_pwd");
        user.setStatus(1);

        when(userDao.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("login_token");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        LoginFrom form = new LoginFrom();
        form.setUsername("test_user");
        form.setPassword("123456");

        LoginVO vo = userService.login(form);
        assertNotNull(vo);
        assertEquals("login_token", vo.getToken());
    }

    @Test
    void login_shouldThrowWhenPasswordWrong() {
        User user = new User();
        user.setPassword("encoded_pwd");
        user.setStatus(1);

        when(userDao.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        LoginFrom form = new LoginFrom();
        form.setUsername("test_user");
        form.setPassword("wrong_pwd");

        assertThrows(RuntimeException.class, () -> userService.login(form));
    }
}
