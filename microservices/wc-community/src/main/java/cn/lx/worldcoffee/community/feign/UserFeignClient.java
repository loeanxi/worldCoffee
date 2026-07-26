package cn.lx.worldcoffee.community.feign;

import cn.lx.worldcoffee.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "wc-user", path = "/api/user")
public interface UserFeignClient {

    /**
     * 批量获取用户信息（通过用户ID列表）
     * 返回 Map<userId, UserInfo>
     */
    @GetMapping("/batch")
    Result<Map<Long, UserInfo>> fetchUsers(@RequestParam("ids") List<Long> ids);

    default Map<Long, UserInfo> batchGetUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Result<Map<Long, UserInfo>> result = fetchUsers(ids);
        if (result == null || result.getData() == null) {
            return Map.of();
        }
        return result.getData();
    }

    record UserInfo(Long id, String username, String avatar) {}
}
