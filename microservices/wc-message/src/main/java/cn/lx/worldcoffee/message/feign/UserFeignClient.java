package cn.lx.worldcoffee.message.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "wc-user", path = "/api/user")
public interface UserFeignClient {

    @GetMapping("/batch")
    Map<Long, UserInfo> batchGetUsers(@RequestParam("ids") List<Long> ids);

    record UserInfo(Long id, String username, String avatar) {}
}
