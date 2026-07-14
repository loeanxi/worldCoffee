package cn.lx.worldcoffee.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {
        "cn.lx.worldcoffee.admin",
        "cn.lx.worldcoffee.common"
})
@MapperScan({
        "cn.lx.worldcoffee.user.dao",
        "cn.lx.worldcoffee.module.shop.dao",
        "cn.lx.worldcoffee.community.dao"
})
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
