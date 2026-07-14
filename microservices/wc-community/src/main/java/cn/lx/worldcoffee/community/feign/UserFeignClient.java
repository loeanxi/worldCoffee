package cn.lx.worldcoffee.community.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "wc-user", path = "/api/user")
public interface UserFeignClient {

    /**
     * 批量获取用户信息（通过用户ID列表）
     * 返回 Map<userId, Map<username, avatar>>
     */
    @GetMapping("/batch")
    Map<Long, Map<String, String>> batchGetUsers(@RequestParam("userIds") String userIds);
}
