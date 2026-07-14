package cn.lx.worldcoffee.admin.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.user.dao.UserDao;
import cn.lx.worldcoffee.user.domain.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserManageService {

    private final UserDao userDao;

    public List<Map<String, Object>> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword.trim())
                    .or()
                    .like(User::getPhone, keyword.trim()));
        }
        wrapper.orderByDesc(User::getCreateTime).last(limit(page, size));
        return userDao.selectList(wrapper).stream()
                .map(this::toUserMap)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        User user = userDao.selectById(userId);
        if (user == null) throw new ServiceException("用户不存在");
        user.setStatus(status);
        userDao.updateById(user);
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("phone", user.getPhone());
        map.put("status", user.getStatus());
        map.put("avatar", user.getAvatar());
        map.put("createTime", user.getCreateTime());
        return map;
    }

    private String limit(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(Math.min(size, 100), 1);
        return "LIMIT " + ((safePage - 1) * safeSize) + "," + safeSize;
    }
}
