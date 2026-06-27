package cn.lx.worldcoffee;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.lx.worldcoffee.module.**.dao")
public class WorldCoffeeApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorldCoffeeApplication.class, args);
    }
}
